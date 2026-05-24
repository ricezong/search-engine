package com.searchengine.task;

import com.searchengine.common.Config;
import com.searchengine.common.DatabaseManager;
import com.searchengine.crawler.BloomFilterManager;
import com.searchengine.crawler.PageDownloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务管理器
 * 管理所有爬取任务的生命周期
 * 维护master数据库存储任务元信息
 */
@Component
public class TaskManager {

    private static final Logger logger = LoggerFactory.getLogger(TaskManager.class);

    private final PageDownloader pageDownloader;
    private final ConcurrentHashMap<String, TaskContext> activeContexts = new ConcurrentHashMap<>();
    private Connection masterConn;

    public TaskManager(PageDownloader pageDownloader) {
        this.pageDownloader = pageDownloader;
    }

    @PostConstruct
    public void init() {
        initMasterDatabase();
    }

    /**
     * 初始化master数据库
     */
    private void initMasterDatabase() {
        try {
            // 确保data目录存在
            Path dataDir = Path.of(Config.DATA_DIR);
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }

            Class.forName("org.sqlite.JDBC");
            String dbUrl = "jdbc:sqlite:" + Config.MASTER_DB_FILE;
            masterConn = DriverManager.getConnection(dbUrl);

            try (Statement stmt = masterConn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS crawl_tasks (
                        task_id TEXT PRIMARY KEY,
                        task_name TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        seed_urls TEXT,
                        max_pages INTEGER DEFAULT 100,
                        crawled_count INTEGER DEFAULT 0,
                        analyzed_count INTEGER DEFAULT 0,
                        indexed_term_count INTEGER DEFAULT 0,
                        create_time BIGINT,
                        update_time BIGINT,
                        error_message TEXT
                    )
                """);
            }

            logger.info("Master数据库初始化成功");

            // 恢复RUNNING状态的任务为STOPPED（应用重启后任务不应继续运行）
            resetRunningTasks();

        } catch (Exception e) {
            logger.error("Master数据库初始化失败", e);
            throw new RuntimeException("Master数据库初始化失败", e);
        }
    }

    /**
     * 将之前RUNNING状态的任务重置为STOPPED
     */
    private void resetRunningTasks() {
        String sql = "UPDATE crawl_tasks SET status = 'STOPPED', update_time = ? WHERE status = 'RUNNING'";
        try (PreparedStatement ps = masterConn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            int count = ps.executeUpdate();
            if (count > 0) {
                logger.info("已将 {} 个运行中的任务重置为STOPPED", count);
            }
        } catch (SQLException e) {
            logger.error("重置运行中任务失败", e);
        }
    }

    /**
     * 创建新任务
     */
    public CrawlTask createTask(String taskName, List<String> seedUrls, int maxPages) {
        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        CrawlTask task = new CrawlTask(taskId, taskName, seedUrls, maxPages);

        String sql = "INSERT INTO crawl_tasks (task_id, task_name, status, seed_urls, max_pages, " +
                     "crawled_count, analyzed_count, indexed_term_count, create_time, update_time) " +
                     "VALUES (?, ?, ?, ?, ?, 0, 0, 0, ?, ?)";
        try (PreparedStatement ps = masterConn.prepareStatement(sql)) {
            ps.setString(1, taskId);
            ps.setString(2, taskName);
            ps.setString(3, task.getStatus().name());
            ps.setString(4, String.join(",", seedUrls));
            ps.setInt(5, maxPages);
            ps.setLong(6, task.getCreateTime());
            ps.setLong(7, task.getUpdateTime());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("创建任务失败", e);
            throw new RuntimeException("创建任务失败", e);
        }

        logger.info("创建任务: taskId={}, name={}", taskId, taskName);
        return task;
    }

    /**
     * 获取任务信息
     */
    public CrawlTask getTask(String taskId) {
        String sql = "SELECT * FROM crawl_tasks WHERE task_id = ?";
        try (PreparedStatement ps = masterConn.prepareStatement(sql)) {
            ps.setString(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapTask(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("获取任务失败: taskId={}", taskId, e);
        }
        return null;
    }

    /**
     * 获取所有任务列表
     */
    public List<CrawlTask> listTasks() {
        List<CrawlTask> tasks = new ArrayList<>();
        String sql = "SELECT * FROM crawl_tasks ORDER BY create_time DESC";
        try (Statement stmt = masterConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tasks.add(mapTask(rs));
            }
        } catch (SQLException e) {
            logger.error("获取任务列表失败", e);
        }
        return tasks;
    }

    /**
     * 更新任务状态
     */
    public void updateTaskStatus(String taskId, TaskStatus status, String errorMessage) {
        String sql = "UPDATE crawl_tasks SET status = ?, update_time = ?, error_message = ? WHERE task_id = ?";
        try (PreparedStatement ps = masterConn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, errorMessage);
            ps.setString(4, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("更新任务状态失败: taskId={}", taskId, e);
        }
    }

    /**
     * 更新任务进度
     */
    public void updateTaskProgress(String taskId, int crawledCount, int analyzedCount, int indexedTermCount) {
        String sql = "UPDATE crawl_tasks SET crawled_count = ?, analyzed_count = ?, " +
                     "indexed_term_count = ?, update_time = ? WHERE task_id = ?";
        try (PreparedStatement ps = masterConn.prepareStatement(sql)) {
            ps.setInt(1, crawledCount);
            ps.setInt(2, analyzedCount);
            ps.setInt(3, indexedTermCount);
            ps.setLong(4, System.currentTimeMillis());
            ps.setString(5, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("更新任务进度失败: taskId={}", taskId, e);
        }
    }

    /**
     * 删除任务及其数据
     */
    public boolean deleteTask(String taskId) {
        // 先检查是否有活跃上下文
        TaskContext ctx = activeContexts.remove(taskId);
        if (ctx != null) {
            ctx.close();
        }

        // 从数据库删除记录
        String sql = "DELETE FROM crawl_tasks WHERE task_id = ?";
        try (PreparedStatement ps = masterConn.prepareStatement(sql)) {
            ps.setString(1, taskId);
            int rows = ps.executeUpdate();

            // 删除任务数据目录
            deleteDirectory(Config.getTaskDataDir(taskId));

            logger.info("删除任务: taskId={}", taskId);
            return rows > 0;
        } catch (SQLException e) {
            logger.error("删除任务失败: taskId={}", taskId, e);
            return false;
        }
    }

    /**
     * 获取任务上下文（懒加载）
     */
    public TaskContext getTaskContext(String taskId) {
        TaskContext ctx = activeContexts.get(taskId);
        if (ctx == null) {
            CrawlTask task = getTask(taskId);
            if (task == null) {
                throw new RuntimeException("任务不存在: " + taskId);
            }

            // 创建任务专属的数据库管理器
            DatabaseManager dbManager = new DatabaseManager(
                Config.getTaskDbFile(taskId),
                Config.getTaskDataDir(taskId),
                Config.getTaskDocRawDir(taskId)
            );

            // 创建布隆过滤器
            BloomFilterManager bloomFilter = new BloomFilterManager(
                Config.getTaskBloomFilterFile(taskId)
            );

            ctx = new TaskContext(task, dbManager, bloomFilter, pageDownloader);
            activeContexts.put(taskId, ctx);
        }
        return ctx;
    }

    /**
     * 移除活跃上下文
     */
    public void removeActiveContext(String taskId) {
        TaskContext ctx = activeContexts.remove(taskId);
        if (ctx != null) {
            ctx.close();
        }
    }

    /**
     * 从ResultSet映射为CrawlTask
     */
    private CrawlTask mapTask(ResultSet rs) throws SQLException {
        CrawlTask task = new CrawlTask();
        task.setTaskId(rs.getString("task_id"));
        task.setTaskName(rs.getString("task_name"));
        task.setStatus(TaskStatus.valueOf(rs.getString("status")));

        String seedUrlsStr = rs.getString("seed_urls");
        if (seedUrlsStr != null && !seedUrlsStr.isEmpty()) {
            task.setSeedUrls(Arrays.asList(seedUrlsStr.split(",")));
        } else {
            task.setSeedUrls(Collections.emptyList());
        }

        task.setMaxPages(rs.getInt("max_pages"));
        task.setCrawledCount(rs.getInt("crawled_count"));
        task.setAnalyzedCount(rs.getInt("analyzed_count"));
        task.setIndexedTermCount(rs.getInt("indexed_term_count"));
        task.setCreateTime(rs.getLong("create_time"));
        task.setUpdateTime(rs.getLong("update_time"));
        task.setErrorMessage(rs.getString("error_message"));
        return task;
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(String dirPath) {
        try {
            Path path = Path.of(dirPath);
            if (Files.exists(path)) {
                Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
                logger.info("删除目录: {}", dirPath);
            }
        } catch (IOException e) {
            logger.error("删除目录失败: {}", dirPath, e);
        }
    }
}