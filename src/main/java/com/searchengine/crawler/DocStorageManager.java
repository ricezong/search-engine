package com.searchengine.crawler;

import com.searchengine.common.Config;
import com.searchengine.common.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网页存储管理器
 * 混合存储方案：网页原始内容存文件，URL与文档编号映射存SQLite
 *
 * 文件存储：每个网页存为一个独立文件，文件名为doc_id
 * SQLite存储：doc_id_map表记录URL与文档编号的对应关系
 *
 * 不再使用@Component注解，由TaskContext按需创建
 */
public class DocStorageManager {

    private static final Logger logger = LoggerFactory.getLogger(DocStorageManager.class);

    private final Connection conn;
    private final String docRawDir;
    private final AtomicInteger docIdCounter;
    private int currentFileIndex = 0;
    private long currentFileSize = 0;

    /**
     * 创建文档存储管理器
     *
     * @param conn      SQLite数据库连接
     * @param docRawDir 网页原始内容存储目录
     */
    public DocStorageManager(Connection conn, String docRawDir) {
        this.conn = conn;
        this.docRawDir = docRawDir;
        this.docIdCounter = new AtomicInteger(initDocIdCounter());
        this.currentFileIndex = initCurrentFileIndex();
    }

    /**
     * 从数据库中获取当前最大的doc_id，初始化计数器
     */
    private int initDocIdCounter() {
        String sql = "SELECT MAX(doc_id) FROM doc_id_map";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                int maxId = rs.getInt(1);
                logger.info("当前最大doc_id: {}", maxId);
                return maxId + 1;
            }
        } catch (SQLException e) {
            logger.error("初始化doc_id计数器失败", e);
        }
        return 1;
    }

    /**
     * 初始化当前文件编号
     */
    private int initCurrentFileIndex() {
        File dir = new File(docRawDir);
        if (!dir.exists()) {
            return 0;
        }
        File[] files = dir.listFiles((d, name) -> name.startsWith("doc_raw_") && name.endsWith(".bin"));
        if (files == null || files.length == 0) {
            return 0;
        }
        int maxIndex = 0;
        for (File f : files) {
            String name = f.getName();
            int idx = Integer.parseInt(name.substring("doc_raw_".length(), name.length() - ".bin".length()));
            if (idx > maxIndex) {
                maxIndex = idx;
            }
            if (idx == maxIndex) {
                currentFileSize = f.length();
            }
        }
        logger.info("当前网页存储文件编号: {}, 大小: {} bytes", maxIndex, currentFileSize);
        return maxIndex;
    }

    /**
     * 存储网页
     * 1. 分配doc_id
     * 2. 将HTML内容写入文件
     * 3. 将URL与doc_id映射写入SQLite
     *
     * @param url     网页URL
     * @param title   网页标题
     * @param content 网页HTML内容
     * @return 分配的doc_id，-1表示失败
     */
    public synchronized int storeDocument(String url, String title, String content) {
        int docId = docIdCounter.getAndIncrement();

        try {
            // 检查当前文件是否超过大小限制
            if (currentFileSize >= Config.DOC_RAW_FILE_MAX_SIZE) {
                currentFileIndex++;
                currentFileSize = 0;
                logger.info("网页存储文件达到上限，切换到新文件: doc_raw_{}.bin", currentFileIndex);
            }

            // 写入网页内容文件
            String filePath = docRawDir + "/doc_raw_" + currentFileIndex + ".bin";
            long offset = FileUtil.appendDocRaw(filePath, url, content);
            currentFileSize = FileUtil.getFileSize(filePath);

            // 写入SQLite映射
            String sql = "INSERT INTO doc_id_map (doc_id, url, title, file_path) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, docId);
                ps.setString(2, url);
                ps.setString(3, title);
                ps.setString(4, filePath + ":" + offset);
                ps.executeUpdate();
            }

            logger.debug("存储网页: docId={}, url={}, file={}:{}", docId, url, filePath, offset);
            return docId;

        } catch (Exception e) {
            logger.error("存储网页失败: url={}", url, e);
            return -1;
        }
    }

    /**
     * 根据doc_id获取URL
     */
    public String getUrlByDocId(int docId) {
        String sql = "SELECT url FROM doc_id_map WHERE doc_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, docId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("url");
                }
            }
        } catch (SQLException e) {
            logger.error("根据doc_id获取URL失败: docId={}", docId, e);
        }
        return null;
    }

    /**
     * 根据URL获取doc_id
     */
    public int getDocIdByUrl(String url) {
        String sql = "SELECT doc_id FROM doc_id_map WHERE url = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, url);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("doc_id");
                }
            }
        } catch (SQLException e) {
            logger.error("根据URL获取doc_id失败: url={}", url, e);
        }
        return -1;
    }

    /**
     * 根据doc_id获取文档标题
     */
    public String getTitleByDocId(int docId) {
        String sql = "SELECT title FROM doc_id_map WHERE doc_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, docId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("title");
                }
            }
        } catch (SQLException e) {
            logger.error("根据doc_id获取标题失败: docId={}", docId, e);
        }
        return null;
    }

    /**
     * 获取已存储的文档总数
     */
    public int getTotalDocCount() {
        String sql = "SELECT COUNT(*) FROM doc_id_map";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("获取文档总数失败", e);
        }
        return 0;
    }
}