package com.searchengine.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 停用词管理器
 * 加载和管理中文停用词表，用于过滤无意义词汇
 * 停用词来源：哈工大停用词表、百度停用词表等常用资源
 */
@Component
public class StopWordManager {

    private static final Logger logger = LoggerFactory.getLogger(StopWordManager.class);

    private final Set<String> stopWords = new HashSet<>();

    /**
     * 初始化停用词表
     * 从 classpath 加载 stopwords.txt 文件
     */
    public StopWordManager() {
        loadStopWords();
    }

    /**
     * 加载停用词表
     */
    private void loadStopWords() {
        try {
            ClassPathResource resource = new ClassPathResource("stopwords.txt");
            if (!resource.exists()) {
                logger.warn("停用词文件不存在，使用默认停用词");
                loadDefaultStopWords();
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        stopWords.add(line.toLowerCase());
                        count++;
                    }
                }
                logger.info("加载停用词表完成，共{}个停用词", count);
            }

        } catch (IOException e) {
            logger.error("加载停用词表失败", e);
            loadDefaultStopWords();
        }
    }

    /**
     * 加载默认停用词（当文件不存在时）
     */
    private void loadDefaultStopWords() {
        // 常见中文停用词
        String[] defaultStopWords = {
            // 语气助词
            "的", "了", "和", "是", "就", "都", "而", "及", "与", "着", "或", "一个",
            // 代词
            "我", "你", "他", "她", "它", "我们", "你们", "他们", "她们", "它们",
            "这", "那", "此", "其", "这个", "那个", "这些", "那些",
            // 介词
            "在", "于", "关于", "按照", "通过", "对于", "为了", "除了",
            // 连词
            "和", "与", "及", "以及", "或者", "还是", "但", "但是", "然而", "虽然",
            // 副词
            "很", "非常", "最", "太", "更", "更加", "尤其", "特别", "十分",
            // 数量词
            "一些", "某些", "各个", "每个", "所有", "全部", "任何",
            // 动词
            "有", "没有", "是", "不是", "能", "不能", "可以", "应该",
            // 标点符号
            "。", "，", "、", "；", "：", "？", "！", "…", "—",
            // 英文停用词
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
            "has", "he", "in", "is", "it", "its", "of", "on", "that", "the",
            "to", "was", "will", "with", "this", "but", "they", "have", "had",
            "what", "when", "where", "who", "which", "why", "how", "all", "each",
            "every", "both", "few", "more", "most", "other", "some", "such",
            "no", "nor", "not", "only", "own", "same", "so", "than", "too",
            "very", "can", "just", "should", "now"
        };

        for (String word : defaultStopWords) {
            stopWords.add(word.toLowerCase());
        }
        logger.info("加载默认停用词{}个", defaultStopWords.length);
    }

    /**
     * 判断是否为停用词
     *
     * @param word 待判断的词
     * @return true 如果是停用词
     */
    public boolean isStopWord(String word) {
        if (word == null || word.isBlank()) {
            return true;
        }
        return stopWords.contains(word.toLowerCase().trim());
    }

    /**
     * 获取停用词总数
     */
    public int getStopWordCount() {
        return stopWords.size();
    }

    /**
     * 添加自定义停用词
     */
    public void addStopWord(String word) {
        if (word != null && !word.isBlank()) {
            stopWords.add(word.toLowerCase().trim());
        }
    }

    /**
     * 移除停用词
     */
    public boolean removeStopWord(String word) {
        if (word != null && !word.isBlank()) {
            return stopWords.remove(word.toLowerCase().trim());
        }
        return false;
    }
}
