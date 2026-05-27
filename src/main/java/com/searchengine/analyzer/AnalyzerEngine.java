package com.searchengine.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分析引擎（优化版）
 * 负责对已爬取的网页进行离线分析：
 * 1. 抽取网页文本信息（增强版 HTML 抽取）
 * 2. 分词并创建临时索引（带停用词过滤和词性标注）
 * 3. 关键词提取（支持 TF-IDF、TextRank 等多种算法）
 *
 * 混合存储方案中，临时索引存入 SQLite 的 temp_index 表
 * 使用 SQL GROUP BY 替代原方案的手动多路归并排序
 *
 * 不再使用@Component 注解，由 TaskContext 按需创建
 */
public class AnalyzerEngine {

    private static final Logger logger = LoggerFactory.getLogger(AnalyzerEngine.class);

    private final HtmlExtractor extractor;
    private final Tokenizer tokenizer;
    private final KeywordExtractor keywordExtractor;
    private final StopWordManager stopWordManager;
    private final Connection conn;
    private final AtomicInteger termIdCounter;
    // term 缓存：避免每次分词都查询数据库，提升性能
    private final Map<String, Integer> termCache = new HashMap<>();
    
    /**
     * 关键词提取模式
     */
    public enum KeywordMode {
        TFIDF,           // TF-IDF 算法
        TEXTRANK,        // TextRank 算法
        POSITION_TFIDF   // 位置权重增强的 TF-IDF
    }
    
    private final KeywordMode keywordMode;

    /**
     * 创建分析引擎（默认 TF-IDF 模式）
     *
     * @param conn SQLite 数据库连接
     */
    public AnalyzerEngine(Connection conn) {
        this(conn, KeywordMode.TFIDF);
    }

    /**
     * 创建分析引擎（指定关键词提取模式）
     *
     * @param conn        SQLite 数据库连接
     * @param keywordMode 关键词提取模式
     */
    public AnalyzerEngine(Connection conn, KeywordMode keywordMode) {
        this.extractor = new HtmlExtractor();
        this.tokenizer = new Tokenizer();
        this.keywordExtractor = new KeywordExtractor();
        this.stopWordManager = new StopWordManager();
        this.tokenizer.setStopWordManager(stopWordManager);
        this.conn = conn;
        this.keywordMode = keywordMode;
        this.termIdCounter = new AtomicInteger(initTermIdCounter());
        loadTermCache();
    }

    /**
     * 从数据库中获取当前最大的 term_id，初始化计数器
     */
    private int initTermIdCounter() {
        String sql = "SELECT MAX(term_id) FROM term_id_map";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                int maxId = rs.getInt(1);
                logger.info("当前最大 term_id: {}", maxId);
                return maxId + 1;
            }
        } catch (SQLException e) {
            logger.error("初始化 term_id 计数器失败", e);
        }
        return 1;
    }

    /**
     * 加载 term 缓存
     */
    private void loadTermCache() {
        String sql = "SELECT term_id, term FROM term_id_map";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                termCache.put(rs.getString("term"), rs.getInt("term_id"));
            }
            logger.info("加载 term 缓存完成，共{}个词", termCache.size());
        } catch (SQLException e) {
            logger.error("加载 term 缓存失败", e);
        }
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
                    return "已分析文档数：" + rs.getInt(1);
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
     * 1. 从 doc_id_map 中获取所有已爬取的文档
     * 2. 从文件中读取网页 HTML 内容
     * 3. 抽取文本、分词（带停用词过滤）
     * 4. 将分词结果写入临时索引（SQLite temp_index 表）
     * 5. 将单词与编号映射写入 term_id_map 表
     * 6. 将文档摘要和关键词写入 doc_meta 表
     */
    public int analyze() {
        logger.info("========== 分析阶段开始 (关键词模式：{}) ==========", keywordMode);

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

                        // 检查是否已分析过（doc_meta 中是否存在）
                        if (isDocAnalyzed(docId)) {
                            continue;
                        }

                        // 分析文档
                        boolean success = analyzeDocument(docId, url, title, filePath);
                        if (success) {
                            analyzedCount++;
                            // 每 100 个文档提交一次事务
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
            logger.error("检查文档分析状态失败：docId={}", docId, e);
        }
        return false;
    }

    /**
     * 分析单个文档
     */
    private boolean analyzeDocument(int docId, String url, String title, String filePath) {
        try {
            // 从文件读取 HTML 内容
            String html = readHtmlFromFile(filePath, url);
            if (html == null || html.isBlank()) {
                logger.warn("文档内容为空：docId={}, url={}", docId, url);
                return false;
            }

            // 抽取文本
            HtmlExtractor.ExtractResult extractResult = extractor.extract(html);
            String text = extractResult.getText();
            if (text.isBlank()) {
                logger.warn("抽取文本为空：docId={}, url={}", docId, url);
                return false;
            }

            // 分词（带停用词过滤）
            List<String> terms = tokenizer.tokenize(text);
            if (terms.isEmpty()) {
                logger.warn("分词结果为空：docId={}, url={}", docId, url);
                return false;
            }

            // 为每个词分配 term_id 并写入临时索引
            for (String term : terms) {
                int termId = getOrCreateTermId(term);
                insertTempIndex(termId, docId);
            }

            // 提取关键词
            List<KeywordExtractor.KeywordResult> keywords = extractKeywords(title, text, terms);
            
            // 写入文档元信息（包含关键词）
            insertDocMeta(docId, extractResult.getSnippet(), terms.size(), 
                          keywordsToString(keywords));

            logger.debug("分析文档：docId={}, url={}, 词数={}, 关键词={}", 
                         docId, url, terms.size(), 
                         keywords.subList(0, Math.min(5, keywords.size())));
            return true;

        } catch (Exception e) {
            logger.error("分析文档失败：docId={}, url={}", docId, url, e);
            return false;
        }
    }

    /**
     * 提取关键词（根据配置的模式）
     */
    private List<KeywordExtractor.KeywordResult> extractKeywords(String title, String text, 
                                                                   List<String> terms) {
        // 计算文档频率 DF（用于 TF-IDF）
        Map<String, Integer> termDocFreq = calculateTermDocFreq(terms);
        int totalDocs = getTotalDocCount();

        switch (keywordMode) {
            case TEXTRANK:
                return keywordExtractor.extractByTextRank(text, terms, 10);
            case POSITION_TFIDF:
                return keywordExtractor.extractByPositionWeightedTFIDF(
                    title, text, terms, totalDocs, termDocFreq, 10);
            case TFIDF:
            default:
                return keywordExtractor.extractByTFIDF(
                    text, terms, totalDocs, termDocFreq, 10);
        }
    }

    /**
     * 计算当前文档的词频（用于 TF-IDF 的 DF 部分）
     */
    private Map<String, Integer> calculateTermDocFreq(List<String> terms) {
        Map<String, Integer> freq = new HashMap<>();
        for (String term : terms) {
            freq.put(term, freq.getOrDefault(term, 0) + 1);
        }
        return freq;
    }

    /**
     * 获取总文档数
     */
    private int getTotalDocCount() {
        String sql = "SELECT COUNT(*) FROM doc_meta";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("获取总文档数失败", e);
        }
        // 返回一个估计值，至少为 1
        return Math.max(1, termCache.size() / 10);
    }

    /**
     * 将关键词列表转换为字符串存储
     */
    private String keywordsToString(List<KeywordExtractor.KeywordResult> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(keywords.get(i).getKeyword());
        }
        return sb.toString();
    }

    /**
     * 从文件中读取 HTML 内容
     * 使用 lastIndexOf(":") 安全分割，兼容 Windows 路径（如 E:\path\...）
     */
    private String readHtmlFromFile(String filePath, String url) {
        try {
            // filePath 格式：data/tasks/{taskId}/doc_raw/doc_raw_0.bin:12345
            // 使用 lastIndexOf 安全分割，避免 Windows 路径中的冒号误分割
            int lastColon = filePath.lastIndexOf(':');
            if (lastColon <= 0) {
                logger.error("文件路径格式错误（缺少偏移量）：{}", filePath);
                return null;
            }
            String filePart = filePath.substring(0, lastColon);
            long offset = Long.parseLong(filePath.substring(lastColon + 1));

            String[] result = com.searchengine.common.FileUtil.readDocRaw(filePart, offset);
            return result[1]; // content
        } catch (Exception e) {
            logger.error("读取文件失败：filePath={}", filePath, e);
            return null;
        }
    }

    /**
     * 获取或创建 term_id
     * 先查缓存，再查数据库，最后创建新编号
     */
    private int getOrCreateTermId(String term) throws SQLException {
        // 1. 先查缓存
        Integer cachedId = termCache.get(term);
        if (cachedId != null) {
            return cachedId;
        }

        // 2. 缓存未命中，查数据库
        String querySql = "SELECT term_id FROM term_id_map WHERE term = ?";
        try (PreparedStatement ps = conn.prepareStatement(querySql)) {
            ps.setString(1, term);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("term_id");
                    termCache.put(term, id);
                    return id;
                }
            }
        }

        // 3. 不存在，创建新的
        int termId = termIdCounter.getAndIncrement();
        String insertSql = "INSERT INTO term_id_map (term_id, term) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setInt(1, termId);
            ps.setString(2, term);
            ps.executeUpdate();
        }
        termCache.put(term, termId);
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
    private void insertDocMeta(int docId, String snippet, int wordCount, String keywords) 
            throws SQLException {
        String sql = "INSERT OR REPLACE INTO doc_meta (doc_id, snippet, word_count, keywords) " +
                     "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, docId);
            ps.setString(2, snippet);
            ps.setInt(3, wordCount);
            ps.setString(4, keywords);
            ps.executeUpdate();
        }
    }
}
