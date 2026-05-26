package com.searchengine.searcher;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.searchengine.common.Config;
import com.searchengine.common.FileUtil;
import com.searchengine.common.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * 搜索引擎
 * 负责响应用户查询请求
 *
 * 查询流程：
 * 1. 对用户输入进行分词
 * 2. 查term_id_map获取单词编号
 * 3. 查term_offset获取倒排索引偏移位置
 * 4. 从index.bin读取倒排列表
 * 5. 统计文档匹配次数并排序
 * 6. 查doc_id_map获取URL和标题
 *
 * 混合存储方案：
 * - term_id_map、term_offset、doc_id_map、doc_meta从SQLite读取
 * - 倒排索引从index.bin文件读取（利用偏移量O(1)定位）
 *
 * 不再使用@Component注解，由TaskContext按需创建
 */
public class SearchEngine {

    private static final Logger logger = LoggerFactory.getLogger(SearchEngine.class);

    private final Connection conn;
    private final String invertedIndexFile;

    // 内存缓存：查询时将小表加载到内存HashMap，加速查找
    private Map<String, Integer> termToIdCache;   // term -> term_id
    private Map<Integer, String> idToTermCache;   // term_id -> term
    private boolean initialized = false;

    /**
     * 创建搜索引擎
     *
     * @param conn              SQLite数据库连接
     * @param invertedIndexFile 倒排索引文件路径
     */
    public SearchEngine(Connection conn, String invertedIndexFile) {
        this.conn = conn;
        this.invertedIndexFile = invertedIndexFile;
    }

    /**
     * 初始化查询引擎
     * 将term_id_map加载到内存HashMap中，加速查询
     */
    public void init() {
        if (initialized) {
            return;
        }
        logger.info("初始化查询引擎，加载缓存...");
        loadTermIdCache();
        initialized = true;
        logger.info("查询引擎初始化完成，缓存词数: {}", termToIdCache.size());
    }

    /**
     * 加载term_id_map到内存
     */
    private void loadTermIdCache() {
        termToIdCache = new HashMap<>();
        idToTermCache = new HashMap<>();

        String sql = "SELECT term_id, term FROM term_id_map";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int termId = rs.getInt("term_id");
                String term = rs.getString("term");
                termToIdCache.put(term, termId);
                idToTermCache.put(termId, term);
            }
        } catch (SQLException e) {
            logger.error("加载term_id_map缓存失败", e);
        }
    }

    /**
     * 执行搜索查询
     *
     * @param query 查询文本
     * @param page  页码（从1开始）
     * @return 搜索结果列表
     */
    public List<SearchResult> search(String query, int page) {
        init(); // 确保缓存已加载
        logger.info("搜索查询: '{}', 页码: {}", query, page);

        // 1. 分词
        List<String> queryTerms = tokenizeQuery(query);
        if (queryTerms.isEmpty()) {
            logger.warn("查询分词结果为空: '{}'", query);
            return Collections.emptyList();
        }
        logger.debug("查询分词结果: {}", queryTerms);

        // 2. 获取每个词的term_id
        List<Integer> termIds = new ArrayList<>();
        for (String term : queryTerms) {
            Integer termId = termToIdCache.get(term);
            if (termId != null) {
                termIds.add(termId);
            } else {
                logger.debug("词不在索引中: '{}'", term);
            }
        }

        if (termIds.isEmpty()) {
            logger.info("查询词均不在索引中: '{}'", query);
            return Collections.emptyList();
        }

        // 3. 获取每个term_id的倒排列表
        Map<Integer, Integer> docMatchCount = new HashMap<>(); // docId -> 匹配次数

        for (int termId : termIds) {
            int[] docIds = getPostingList(termId);
            for (int docId : docIds) {
                docMatchCount.merge(docId, 1, Integer::sum);
            }
        }

        if (docMatchCount.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. 按匹配次数排序
        List<Map.Entry<Integer, Integer>> sortedDocs = new ArrayList<>(docMatchCount.entrySet());
        sortedDocs.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        // 5. 分页获取结果
        int startIndex = (page - 1) * Config.SEARCH_RESULTS_PER_PAGE;
        int endIndex = Math.min(startIndex + Config.SEARCH_RESULTS_PER_PAGE, sortedDocs.size());

        List<SearchResult> results = new ArrayList<>();
        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<Integer, Integer> entry = sortedDocs.get(i);
            int docId = entry.getKey();
            int matchCount = entry.getValue();

            SearchResult result = buildSearchResult(docId, matchCount);
            if (result != null) {
                results.add(result);
            }
        }

        logger.info("搜索完成: 查询='{}', 匹配文档数={}, 返回结果数={}",
                query, docMatchCount.size(), results.size());

        return results;
    }

    /**
     * 对查询文本进行分词
     */
    private List<String> tokenizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        List<Term> terms = HanLP.segment(query);
        List<String> result = new ArrayList<>();

        for (Term term : terms) {
            String word = term.word.trim().toLowerCase();
            if (word.length() >= Config.MIN_TERM_LENGTH && !isPunctuation(word)) {
                if (!result.contains(word)) {
                    result.add(word);
                }
            }
        }
        return result;
    }

    /**
     * 判断是否为标点符号
     */
    private boolean isPunctuation(String word) {
        if (word.length() != 1) return false;
        char c = word.charAt(0);
        return !Character.isLetterOrDigit(c);
    }

    /**
     * 获取指定term_id的倒排列表
     * 从SQLite term_offset表获取偏移量，再从index.bin文件读取
     */
    private int[] getPostingList(int termId) {
        // 从SQLite获取偏移量和长度
        long offset = -1;
        int length = -1;

        String sql = "SELECT offset_val, length FROM term_offset WHERE term_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, termId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    offset = rs.getLong("offset_val");
                    length = rs.getInt("length");
                }
            }
        } catch (SQLException e) {
            logger.error("查询term_offset失败: termId={}", termId, e);
            return new int[0];
        }

        if (offset < 0 || length <= 0) {
            logger.debug("term_id={} 无倒排索引记录", termId);
            return new int[0];
        }

        // 从index.bin文件读取倒排列表
        try {
            return FileUtil.readInvertedIndex(invertedIndexFile, offset, length);
        } catch (IOException e) {
            logger.error("读取倒排索引失败: termId={}, offset={}, length={}", termId, offset, length, e);
            return new int[0];
        }
    }

    /**
     * 构建搜索结果
     */
    private SearchResult buildSearchResult(int docId, int matchCount) {
        String sql = """
            SELECT d.url, d.title, m.snippet
            FROM doc_id_map d
            LEFT JOIN doc_meta m ON d.doc_id = m.doc_id
            WHERE d.doc_id = ?
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, docId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String url = rs.getString("url");
                    String title = rs.getString("title");
                    String snippet = rs.getString("snippet");

                    if (title == null || title.isBlank()) {
                        title = url;
                    }
                    if (snippet == null) {
                        snippet = "";
                    }

                    return new SearchResult(docId, url, title, snippet, matchCount);
                }
            }
        } catch (SQLException e) {
            logger.error("构建搜索结果失败: docId={}", docId, e);
        }
        return null;
    }

    /**
     * 获取搜索结果总数（不分页）
     */
    public int getTotalMatchCount(String query) {
        init(); // 确保缓存已加载
        List<String> queryTerms = tokenizeQuery(query);
        if (queryTerms.isEmpty()) {
            return 0;
        }

        Set<Integer> matchedDocs = new HashSet<>();
        for (String term : queryTerms) {
            Integer termId = termToIdCache.get(term);
            if (termId != null) {
                int[] docIds = getPostingList(termId);
                for (int docId : docIds) {
                    matchedDocs.add(docId);
                }
            }
        }
        return matchedDocs.size();
    }

    /**
     * 获取热门关键词（按倒排索引中的文档数排序）
     *
     * @param limit 返回数量
     * @return 关键词列表
     */
    public List<String> getTopKeywords(int limit) {
        init(); // 确保缓存已加载

        String sql = "SELECT t.term, o.length FROM term_id_map t " +
                "JOIN term_offset o ON t.term_id = o.term_id " +
                "ORDER BY o.length DESC LIMIT ?";
        List<String> keywords = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String term = rs.getString("term");
                    if (term != null && !term.isBlank()) {
                        keywords.add(term);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("获取热门关键词失败", e);
        }
        return keywords;
    }
}