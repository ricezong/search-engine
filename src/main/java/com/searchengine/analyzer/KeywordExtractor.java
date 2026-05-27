package com.searchengine.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 关键词提取器（优化版）
 * 实现多种业界主流关键词提取算法：
 * 1. TF-IDF：词频 - 逆文档频率，经典统计方法
 * 2. TextRank：基于图的排序算法，无需训练数据
 * 3. 位置权重：考虑词语在文档中的位置（标题、开头等）
 */
@Component
public class KeywordExtractor {

    private static final Logger logger = LoggerFactory.getLogger(KeywordExtractor.class);

    /**
     * 默认关键词数量
     */
    private static final int DEFAULT_KEYWORD_COUNT = 10;

    /**
     * TextRank 迭代次数
     */
    private static final int TEXTRANK_ITERATIONS = 10;

    /**
     * TextRank 阻尼系数
     */
    private static final double DAMPING_FACTOR = 0.85;

    /**
     * 使用 TF-IDF 算法提取关键词
     *
     * @param text        文档文本
     * @param terms       分词结果
     * @param docCount    总文档数
     * @param termDocFreq 每个词出现的文档数（倒排索引长度）
     * @param topK        返回前 K 个关键词
     * @return 关键词列表（按 TF-IDF 分数降序）
     */
    public List<KeywordResult> extractByTFIDF(String text, List<String> terms, 
                                               int docCount, Map<String, Integer> termDocFreq, 
                                               int topK) {
        if (text == null || text.isBlank() || terms == null || terms.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 计算词频 TF
        Map<String, Integer> termFreq = new HashMap<>();
        for (String term : terms) {
            termFreq.put(term, termFreq.getOrDefault(term, 0) + 1);
        }

        // 2. 计算 TF-IDF
        Map<String, Double> tfidfScores = new HashMap<>();
        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();
            int df = termDocFreq.getOrDefault(term, 1);

            // TF 归一化
            double normalizedTf = (double) tf / terms.size();

            // IDF 计算：log(N/df)
            double idf = Math.log((double) docCount / df);

            // TF-IDF
            double tfidf = normalizedTf * idf;
            tfidfScores.put(term, tfidf);
        }

        // 3. 排序并返回 topK
        return sortByScore(tfidfScores, topK);
    }

    /**
     * 使用 TextRank 算法提取关键词
     * 基于图的排序算法，考虑词语之间的共现关系
     *
     * @param text  文档文本
     * @param terms 分词结果
     * @param topK  返回前 K 个关键词
     * @return 关键词列表（按 TextRank 分数降序）
     */
    public List<KeywordResult> extractByTextRank(String text, List<String> terms, int topK) {
        if (text == null || text.isBlank() || terms == null || terms.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 构建共现图（窗口大小为 5）
        int windowSize = 5;
        Map<String, Set<String>> graph = new HashMap<>();

        for (int i = 0; i < terms.size(); i++) {
            String word = terms.get(i);
            graph.putIfAbsent(word, new HashSet<>());

            // 与窗口内的其他词建立连接
            for (int j = i + 1; j < Math.min(i + windowSize, terms.size()); j++) {
                String otherWord = terms.get(j);
                if (!word.equals(otherWord)) {
                    graph.get(word).add(otherWord);
                    graph.putIfAbsent(otherWord, new HashSet<>());
                    graph.get(otherWord).add(word);
                }
            }
        }

        // 2. PageRank 迭代计算
        Map<String, Double> scores = new HashMap<>();
        for (String word : graph.keySet()) {
            scores.put(word, 1.0);
        }

        for (int iter = 0; iter < TEXTRANK_ITERATIONS; iter++) {
            Map<String, Double> newScores = new HashMap<>();

            for (String word : graph.keySet()) {
                double score = (1.0 - DAMPING_FACTOR);

                Set<String> neighbors = graph.get(word);
                if (!neighbors.isEmpty()) {
                    for (String neighbor : neighbors) {
                        Set<String> neighborNeighbors = graph.get(neighbor);
                        if (!neighborNeighbors.isEmpty()) {
                            score += DAMPING_FACTOR * scores.get(neighbor) / neighborNeighbors.size();
                        }
                    }
                }

                newScores.put(word, score);
            }

            scores = newScores;
        }

        // 3. 排序并返回 topK
        return sortByScore(scores, topK);
    }

    /**
     * 使用位置权重增强的 TF-IDF 提取关键词
     * 标题和文档开头的词赋予更高权重
     *
     * @param title       文档标题
     * @param text        文档正文
     * @param terms       分词结果
     * @param docCount    总文档数
     * @param termDocFreq 每个词出现的文档数
     * @param topK        返回前 K 个关键词
     * @return 关键词列表
     */
    public List<KeywordResult> extractByPositionWeightedTFIDF(String title, String text, 
                                                               List<String> terms, 
                                                               int docCount, 
                                                               Map<String, Integer> termDocFreq, 
                                                               int topK) {
        if ((text == null || text.isBlank()) && (title == null || title.isBlank())) {
            return new ArrayList<>();
        }

        // 1. 标记标题中的词
        Set<String> titleTerms = new HashSet<>();
        if (title != null && !title.isBlank()) {
            titleTerms.addAll(terms); // 简化处理，实际应该对标题单独分词
        }

        // 2. 计算词频（考虑位置权重）
        Map<String, Double> weightedTermFreq = new HashMap<>();
        for (int i = 0; i < terms.size(); i++) {
            String term = terms.get(i);
            double weight = 1.0;

            // 标题中的词权重更高
            if (titleTerms.contains(term)) {
                weight *= 2.0;
            }

            // 文档开头的词权重较高（前 10%）
            if (i < terms.size() * 0.1) {
                weight *= 1.5;
            }

            weightedTermFreq.put(term, weightedTermFreq.getOrDefault(term, 0.0) + weight);
        }

        // 3. 计算 TF-IDF
        Map<String, Double> tfidfScores = new HashMap<>();
        for (Map.Entry<String, Double> entry : weightedTermFreq.entrySet()) {
            String term = entry.getKey();
            double tf = entry.getValue();
            int df = termDocFreq.getOrDefault(term, 1);

            // TF 归一化
            double totalWeight = weightedTermFreq.values().stream().mapToDouble(Double::doubleValue).sum();
            double normalizedTf = tf / totalWeight;

            // IDF 计算
            double idf = Math.log((double) docCount / df);

            // TF-IDF
            double tfidf = normalizedTf * idf;
            tfidfScores.put(term, tfidf);
        }

        // 4. 排序并返回 topK
        return sortByScore(tfidfScores, topK);
    }

    /**
     * 按分数排序并返回 topK
     */
    private List<KeywordResult> sortByScore(Map<String, Double> scores, int topK) {
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(scores.entrySet());
        sortedEntries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<KeywordResult> result = new ArrayList<>();
        int count = Math.min(topK, sortedEntries.size());
        for (int i = 0; i < count; i++) {
            Map.Entry<String, Double> entry = sortedEntries.get(i);
            result.add(new KeywordResult(entry.getKey(), entry.getValue()));
        }

        return result;
    }

    /**
     * 关键词结果类
     */
    public static class KeywordResult {
        private final String keyword;
        private final double score;

        public KeywordResult(String keyword, double score) {
            this.keyword = keyword;
            this.score = score;
        }

        public String getKeyword() {
            return keyword;
        }

        public double getScore() {
            return score;
        }

        @Override
        public String toString() {
            return String.format("%s:%.4f", keyword, score);
        }
    }
}
