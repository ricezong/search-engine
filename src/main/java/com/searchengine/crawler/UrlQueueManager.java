package com.searchengine.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.List;

/**
 * URL队列管理器
 * 使用SQLite存储URL队列
 * 提供URL的入队、出队、状态管理功能
 *
 * URL状态流转：PENDING -> CRAWLING -> CRAWLED / FAILED
 *
 * 不再使用@Component注解，由TaskContext按需创建
 */
public class UrlQueueManager {

    private static final Logger logger = LoggerFactory.getLogger(UrlQueueManager.class);

    private final Connection conn;

    /**
     * 创建URL队列管理器
     *
     * @param conn SQLite数据库连接
     */
    public UrlQueueManager(Connection conn) {
        this.conn = conn;
    }

    /**
     * 添加种子URL
     */
    public void addSeedUrls(List<String> seedUrls) {
        String sql = "INSERT OR IGNORE INTO url_queue (url, status, depth) VALUES (?, 'PENDING', 0)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String url : seedUrls) {
                ps.setString(1, url);
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            int added = 0;
            for (int r : results) {
                if (r > 0) added++;
            }
            logger.info("添加种子URL: 尝试={}, 成功={}", seedUrls.size(), added);
        } catch (SQLException e) {
            logger.error("添加种子URL失败", e);
        }
    }

    /**
     * 从队列中取出一个待爬取的URL
     * 使用SELECT + UPDATE实现原子操作，避免并发问题
     */
    public synchronized UrlTask poll() {
        String selectSql = "SELECT id, url, depth FROM url_queue WHERE status = 'PENDING' ORDER BY id ASC LIMIT 1";
        String updateSql = "UPDATE url_queue SET status = 'CRAWLING', update_time = CURRENT_TIMESTAMP WHERE id = ?";

        try {
            // 查询待爬取URL
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(selectSql)) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String url = rs.getString("url");
                    int depth = rs.getInt("depth");

                    // 更新状态为爬取中
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }

                    return new UrlTask(id, url, depth);
                }
            }
        } catch (SQLException e) {
            logger.error("从URL队列取任务失败", e);
        }
        return null;
    }

    /**
     * 标记URL爬取成功
     */
    public void markCrawled(int taskId) {
        String sql = "UPDATE url_queue SET status = 'CRAWLED', update_time = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("标记URL爬取成功失败, taskId={}", taskId, e);
        }
    }

    /**
     * 标记URL爬取失败
     */
    public void markFailed(int taskId) {
        String sql = "UPDATE url_queue SET status = 'FAILED', update_time = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("标记URL爬取失败失败, taskId={}", taskId, e);
        }
    }

    /**
     * 将新发现的URL添加到队列
     * 使用布隆过滤器去重，并限制总数量不超过maxPages
     * 使用synchronized确保原子性，避免并发超量添加
     */
    public synchronized int addNewUrls(List<String> urls, int depth, BloomFilterManager bloomFilter, int maxPages) {
        // 计算当前所有URL的总数：已处理（已爬取+失败） + 待爬取 + 正在爬取中
        int totalNow = getProcessedCount() + getPendingCount() + getCrawlingCount();
        int canAdd = Math.max(0, maxPages - totalNow);
        if (canAdd <= 0) {
            return 0;
        }

        String sql = "INSERT OR IGNORE INTO url_queue (url, status, depth) VALUES (?, 'PENDING', ?)";
        int added = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String url : urls) {
                if (added >= canAdd) {
                    break; // 达到允许添加的上限
                }
                // 先用布隆过滤器快速判断
                if (bloomFilter.mightContain(url)) {
                    continue;
                }
                bloomFilter.put(url);

                ps.setString(1, url);
                ps.setInt(2, depth);
                ps.addBatch();
                added++;
            }
            if (added > 0) {
                ps.executeBatch();
            }
        } catch (SQLException e) {
            logger.error("添加新URL失败", e);
        }
        return added;
    }

    /**
     * 获取待爬取URL数量
     */
    public int getPendingCount() {
        String sql = "SELECT COUNT(*) FROM url_queue WHERE status = 'PENDING'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("获取待爬取URL数量失败", e);
        }
        return 0;
    }

    /**
     * 获取正在爬取中的URL数量
     */
    public int getCrawlingCount() {
        String sql = "SELECT COUNT(*) FROM url_queue WHERE status = 'CRAWLING'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("获取爬取中URL数量失败", e);
        }
        return 0;
    }

    /**
     * 获取已爬取URL数量
     */
    public int getCrawledCount() {
        String sql = "SELECT COUNT(*) FROM url_queue WHERE status = 'CRAWLED'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("获取已爬取URL数量失败", e);
        }
        return 0;
    }

    /**
     * 获取失败URL数量
     */
    public int getFailedCount() {
        String sql = "SELECT COUNT(*) FROM url_queue WHERE status = 'FAILED'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("获取失败URL数量失败", e);
        }
        return 0;
    }

    /**
     * 获取已处理URL数量（已爬取 + 失败）
     */
    public int getProcessedCount() {
        String sql = "SELECT COUNT(*) FROM url_queue WHERE status IN ('CRAWLED', 'FAILED')";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("获取已处理URL数量失败", e);
        }
        return 0;
    }

    /**
     * URL任务
     */
    public static class UrlTask {
        private final int id;
        private final String url;
        private final int depth;

        public UrlTask(int id, String url, int depth) {
            this.id = id;
            this.url = url;
            this.depth = depth;
        }

        public int getId() {
            return id;
        }

        public String getUrl() {
            return url;
        }

        public int getDepth() {
            return depth;
        }
    }
}