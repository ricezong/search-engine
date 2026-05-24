package com.searchengine.indexer;

import com.searchengine.common.Config;
import com.searchengine.common.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.sql.*;

/**
 * 索引引擎
 * 负责从临时索引构建倒排索引
 *
 * 混合存储方案：
 * - 临时索引从SQLite temp_index表读取，使用SQL GROUP BY替代手动多路归并排序
 * - 倒排索引写入文件index.bin（保持O(1)偏移定位优势）
 * - 偏移量写入SQLite term_offset表
 *
 * 倒排索引文件格式（index.bin）：
 * [4字节term_id][4字节文档数量N][N个4字节doc_id]
 *
 * 不再使用@Component注解，由TaskContext按需创建
 */
public class IndexerEngine {

    private static final Logger logger = LoggerFactory.getLogger(IndexerEngine.class);

    private final Connection conn;
    private final String invertedIndexFile;

    /**
     * 创建索引引擎
     *
     * @param conn              SQLite数据库连接
     * @param invertedIndexFile 倒排索引文件路径
     */
    public IndexerEngine(Connection conn, String invertedIndexFile) {
        this.conn = conn;
        this.invertedIndexFile = invertedIndexFile;
    }

    /**
     * 构建倒排索引
     * 步骤：
     * 1. 清除旧的倒排索引
     * 2. 从SQLite temp_index按term_id分组查询
     * 3. 逐组写入index.bin文件
     * 4. 记录每个term_id在index.bin中的偏移量到term_offset表
     */
    public void build() {
        logger.info("========== 索引构建开始 ==========");

        try {
            // 1. 清除旧索引
            clearOldIndex();

            // 2. 确保输出目录存在
            File indexFile = new File(invertedIndexFile);
            File parentDir = indexFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 3. 删除旧的index.bin文件
            if (indexFile.exists()) {
                indexFile.delete();
                logger.info("删除旧的倒排索引文件");
            }

            // 4. 从SQLite读取临时索引，按term_id分组
            // 使用SQL GROUP_CONCAT聚合doc_id，替代手动多路归并排序
            String sql = """
                SELECT term_id, GROUP_CONCAT(doc_id, ',') AS doc_ids, COUNT(*) AS doc_count
                FROM temp_index
                GROUP BY term_id
                ORDER BY term_id
                """;

            long totalTerms = 0;
            long totalPostings = 0;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                // 开启事务
                conn.setAutoCommit(false);

                try (RandomAccessFile raf = new RandomAccessFile(invertedIndexFile, "rw");
                     FileChannel channel = raf.getChannel()) {

                    String insertOffsetSql = "INSERT INTO term_offset (term_id, offset_val, length) VALUES (?, ?, ?)";
                    try (PreparedStatement psOffset = conn.prepareStatement(insertOffsetSql)) {

                        int batchCount = 0;

                        while (rs.next()) {
                            int termId = rs.getInt("term_id");
                            String docIdsStr = rs.getString("doc_ids");
                            int docCount = rs.getInt("doc_count");

                            // 解析doc_id列表
                            int[] docIds = parseDocIds(docIdsStr, docCount);

                            // 写入index.bin
                            long offset = channel.position();
                            int length = writeInvertedIndexEntry(channel, termId, docIds);

                            // 记录偏移量到SQLite
                            psOffset.setInt(1, termId);
                            psOffset.setLong(2, offset);
                            psOffset.setInt(3, length);
                            psOffset.addBatch();
                            batchCount++;

                            totalTerms++;
                            totalPostings += docCount;

                            // 每1000个term提交一次
                            if (batchCount >= 1000) {
                                psOffset.executeBatch();
                                conn.commit();
                                batchCount = 0;
                                logger.info("已处理 {} 个term", totalTerms);
                            }
                        }

                        // 提交剩余
                        if (batchCount > 0) {
                            psOffset.executeBatch();
                        }
                        conn.commit();
                    }

                } catch (IOException e) {
                    throw new RuntimeException("写入倒排索引文件失败", e);
                }

            } finally {
                conn.setAutoCommit(true);
            }

            logger.info("========== 索引构建完成: term数={}, 倒排记录数={}, 文件大小={}字节 ==========",
                    totalTerms, totalPostings, FileUtil.getFileSize(invertedIndexFile));

        } catch (Exception e) {
            logger.error("索引构建失败", e);
        }
    }

    /**
     * 清除旧的倒排索引数据
     */
    private void clearOldIndex() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM term_offset");
            logger.info("清除旧的term_offset数据");
        }
    }

    /**
     * 解析GROUP_CONCAT结果为doc_id数组
     */
    private int[] parseDocIds(String docIdsStr, int expectedCount) {
        if (docIdsStr == null || docIdsStr.isEmpty()) {
            return new int[0];
        }

        String[] parts = docIdsStr.split(",");
        int[] docIds = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            docIds[i] = Integer.parseInt(parts[i].trim());
        }
        return docIds;
    }

    /**
     * 写入一条倒排索引记录到index.bin
     * 格式：[4字节term_id][4字节文档数量N][N个4字节doc_id]
     *
     * @return 写入的字节数
     */
    private int writeInvertedIndexEntry(FileChannel channel, int termId, int[] docIds) throws IOException {
        int recordSize = 4 + 4 + docIds.length * 4; // termId + count + docIds
        ByteBuffer buffer = ByteBuffer.allocate(recordSize);
        buffer.putInt(termId);
        buffer.putInt(docIds.length);
        for (int docId : docIds) {
            buffer.putInt(docId);
        }
        buffer.flip();
        channel.write(buffer);
        return recordSize;
    }

    /**
     * 获取索引统计信息
     */
    public String getStats() {
        try {
            int termCount = 0;
            int postingCount = 0;

            // 从 term_offset 表获取 term 数量
            String termSql = "SELECT COUNT(*) AS term_count FROM term_offset";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(termSql)) {
                if (rs.next()) {
                    termCount = rs.getInt("term_count");
                }
            }

            // 从 temp_index 表获取总倒排记录数
            String postingSql = "SELECT COUNT(*) AS total_postings FROM temp_index";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(postingSql)) {
                if (rs.next()) {
                    postingCount = rs.getInt("total_postings");
                }
            }

            long fileSize = FileUtil.getFileSize(invertedIndexFile);

            return String.format("索引词数: %d, 倒排记录数: %d, 索引文件大小: %d字节",
                    termCount, postingCount, fileSize);

        } catch (SQLException e) {
            logger.error("获取索引统计信息失败", e);
            return "统计信息获取失败";
        }
    }

    /**
     * 获取索引词数
     */
    public int getTermCount() {
        String sql = "SELECT COUNT(*) FROM term_offset";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("获取索引词数失败", e);
        }
        return 0;
    }
}