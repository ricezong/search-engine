package com.searchengine.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分析引擎
 * 负责对已爬取的网页进行离线分析：
 * 1. 抽取网页文本信息
 * 2. 分词并创建临时索引
 *
 * 混合存储方案中，临时索引存入SQLite的temp_index表
 * 使用SQL GROUP BY替代原方案的手动多路归并排序
 *
 * 不再使用@Component注解，由TaskContext按需创建
 */
public class AnalyzerEngine {

    private static final Logger logger = LoggerFactory.getLogger(AnalyzerEngine.class);

    private final HtmlExtractor extractor;
    private final Tokenizer tokenizer;
    private final Connection conn;
    private final AtomicInteger termIdCounter;

    /**
     * 创建分析引擎
     *
     * @param conn SQLite数据库连接
     */
    public AnalyzerEngine(Connection conn) {
        this.extractor = new HtmlExtractor();
        this.tokenizer = new Tokenizer();
        this.conn = conn;
        this.termIdCounter = new AtomicInteger(initTermIdCounter());
    }

    /**
     * 从数据库中获取当前最大的term_id，初始化计数器
     */
    private int initTermIdCounter() {
        String sql = "SELECT MAX(term_id) FROM term_id_map";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                int maxId = rs.getInt(1);
                logger.info("当前最大term_id: {}", maxId);
                return maxId + 1;
            }
        } catch (SQLException e) {
            logger.error("初始化term_id计数器失败", e);
        }
        return 1;
    }

    /**
     * 分析所有已爬取但未分析的网页（别名方法）
     */
    public void analyzeAll() {
        analyze();
    }

    /**
     * 获取分析统计信息
     */
    public String getStats() {
        try {
            String sql = "SELECT COUNT(*) FROM doc_meta";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return "已分析文档数: " + rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("获取分析统计信息失败", e);
        }
        return "统计信息获取失败";
    }

    /**
     * 获取已分析文档数量
     */
    public int getAnalyzedCount() {
        String sql = "SELECT COUNT(*) FROM doc_meta";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("获取已分析文档数量失败", e);
        }
        return 0;
    }

    /**
     * 对所有已爬取但未分析的网页执行分析
     * 分析流程：
     * 1. 从doc_id_map中获取所有已爬取的文档
     * 2. 从文件中读取网页HTML内容
     * 3. 抽取文本、分词
     * 4. 将分词结果写入临时索引（SQLite temp_index表）
     * 5. 将单词与编号映射写入term_id_map表
     * 6. 将文档摘要写入doc_meta表
     */
    public int analyze() {
        logger.info("========== 分析阶段开始 ==========");

        int analyzedCount = 0;

        try {
            // 获取所有已存储的文档
            String querySql = "SELECT doc_id, url, title, file_path FROM doc_id_map ORDER BY doc_id";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(querySql)) {

                // 开启事务，批量写入
                conn.setAutoCommit(false);

                try {
                    while (rs.next()) {
                        int docId = rs.getInt("doc_id");
                        String url = rs.getString("url");
                        String title = rs.getString("title");
                        String filePath = rs.getString("file_path");

                        // 检查是否已分析过（doc_meta中是否存在）
                        if (isDocAnalyzed(docId)) {
                            continue;
                        }

                        // 分析文档
                        boolean success = analyzeDocument(docId, url, title, filePath);
                        if (success) {
                            analyzedCount++;
                            // 每100个文档提交一次事务
                            if (analyzedCount % 100 == 0) {
                                conn.commit();
                                logger.info("已分析 {} 个文档", analyzedCount);
                            }
                        }
                    }

                    // 提交剩余事务
                    conn.commit();

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }

        } catch (Exception e) {
            logger.error("分析阶段失败", e);
        }

        logger.info("========== 分析阶段完成，共分析 {} 个文档 ==========", analyzedCount);
        return analyzedCount;
    }

    /**
     * 检查文档是否已被分析过
     */
    private boolean isDocAnalyzed(int docId) {
        String sql = "SELECT COUNT(*) FROM doc_meta WHERE doc_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, docId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("检查文档分析状态失败: docId={}", docId, e);
        }
        return false;
    }

    /**
     * 分析单个文档
     */
    private boolean analyzeDocument(int docId, String url, String title, String filePath) {
        try {
            // 从文件读取HTML内容
            String html = readHtmlFromFile(filePath, url);
            if (html == null || html.isBlank()) {
                logger.warn("文档内容为空: docId={}, url={}", docId, url);
                return false;
            }

            // 抽取文本
            HtmlExtractor.ExtractResult extractResult = extractor.extract(html);
            String text = extractResult.getText();
            if (text.isBlank()) {
                logger.warn("抽取文本为空: docId={}, url={}", docId, url);
                return false;
            }

            // 分词
            List<String> terms = tokenizer.tokenize(text);
            if (terms.isEmpty()) {
                logger.warn("分词结果为空: docId={}, url={}", docId, url);
                return false;
            }

            // 为每个词分配term_id并写入临时索引
            for (String term : terms) {
                int termId = getOrCreateTermId(term);
                insertTempIndex(termId, docId);
            }

            // 写入文档元信息
            insertDocMeta(docId, extractResult.getSnippet(), terms.size());

            logger.debug("分析文档: docId={}, url={}, 词数={}", docId, url, terms.size());
            return true;

        } catch (Exception e) {
            logger.error("分析文档失败: docId={}, url={}", docId, url, e);
            return false;
        }
    }

    /**
     * 从文件中读取HTML内容
     */
    private String readHtmlFromFile(String filePath, String url) {
        try {
            // filePath格式: data/tasks/{taskId}/doc_raw/doc_raw_0.bin:12345
            String[] parts = filePath.split(":");
            if (parts.length < 2) {
                logger.error("文件路径格式错误: {}", filePath);
                return null;
            }
            // 重新拼接（Windows路径可能包含冒号）
            String filePart = parts[0];
            long offset = Long.parseLong(parts[parts.length - 1]);

            String[] result = com.searchengine.common.FileUtil.readDocRaw(filePart, offset);
            return result[1]; // content
        } catch (Exception e) {
            logger.error("读取文件失败: filePath={}", filePath, e);
            return null;
        }
    }

    /**
     * 获取或创建term_id
     * 先查SQLite，不存在则分配新编号
     */
    private int getOrCreateTermId(String term) throws SQLException {
        // 先查询
        String querySql = "SELECT term_id FROM term_id_map WHERE term = ?";
        try (PreparedStatement ps = conn.prepareStatement(querySql)) {
            ps.setString(1, term);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("term_id");
                }
            }
        }

        // 不存在，创建新的
        int termId = termIdCounter.getAndIncrement();
        String insertSql = "INSERT INTO term_id_map (term_id, term) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setInt(1, termId);
            ps.setString(2, term);
            ps.executeUpdate();
        }
        return termId;
    }

    /**
     * 插入临时索引记录
     */
    private void insertTempIndex(int termId, int docId) throws SQLException {
        String sql = "INSERT INTO temp_index (term_id, doc_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, termId);
            ps.setInt(2, docId);
            ps.executeUpdate();
        }
    }

    /**
     * 插入文档元信息
     */
    private void insertDocMeta(int docId, String snippet, int wordCount) throws SQLException {
        String sql = "INSERT OR REPLACE INTO doc_meta (doc_id, snippet, word_count) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, docId);
            ps.setString(2, snippet);
            ps.setInt(3, wordCount);
            ps.executeUpdate();
        }
    }
}