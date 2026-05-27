package com.searchengine.analyzer;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.searchengine.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 中文分词器（优化版）
 * 使用 HanLP 进行中文分词，集成停用词过滤和词性标注
 * 支持多种优化策略：
 * 1. 停用词过滤：移除无意义的高频虚词
 * 2. 词性筛选：保留名词、动词、形容词等有实际意义的词
 * 3. 长度过滤：过滤过短或过长的词
 * 4. 数字过滤：过滤纯数字和无关紧要的数值
 */
@Component
public class Tokenizer {

    private static final Logger logger = LoggerFactory.getLogger(Tokenizer.class);

    @Autowired
    private StopWordManager stopWordManager;

    /**
     * 对文本进行分词（带停用词过滤）
     * 过滤掉标点符号、停用词、过短的词和纯数字
     *
     * @param text 待分词文本
     * @return 分词结果列表（已去重）
     */
    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        List<Term> terms = HanLP.segment(text);
        Set<String> resultSet = new HashSet<>();

        for (Term term : terms) {
            String word = term.word.trim();
            
            // 1. 长度过滤
            if (word.length() < Config.MIN_TERM_LENGTH || word.length() > 50) {
                continue;
            }
            
            // 2. 停用词过滤
            if (stopWordManager != null && stopWordManager.isStopWord(word)) {
                continue;
            }
            
            // 3. 纯数字过滤
            if (isPureNumber(word)) {
                continue;
            }
            
            // 4. 转小写统一
            word = word.toLowerCase();
            resultSet.add(word);
        }

        List<String> result = new ArrayList<>(resultSet);
        logger.debug("分词结果：输入长度={}, 输出词数={}", text.length(), result.size());
        return result;
    }

    /**
     * 对文本进行分词（带词性筛选）
     * 只保留指定词性的词语，适合更精确的关键词提取
     *
     * @param text 待分词文本
     * @param keepNatures 要保留的词性列表，如 ["n", "v", "a"]
     *        n: 名词，v: 动词，a: 形容词，d: 副词
     * @return 分词结果列表
     */
    public List<String> tokenizeWithNature(String text, List<String> keepNatures) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        List<Term> terms = HanLP.segment(text);
        Set<String> resultSet = new HashSet<>();

        for (Term term : terms) {
            String word = term.word.trim();
            String nature = term.nature.toString();

            // 1. 词性筛选
            if (keepNatures != null && !keepNatures.isEmpty()) {
                boolean match = false;
                for (String keepNature : keepNatures) {
                    if (nature.startsWith(keepNature)) {
                        match = true;
                        break;
                    }
                }
                if (!match) {
                    continue;
                }
            }

            // 2. 长度过滤
            if (word.length() < Config.MIN_TERM_LENGTH || word.length() > 50) {
                continue;
            }

            // 3. 停用词过滤
            if (stopWordManager != null && stopWordManager.isStopWord(word)) {
                continue;
            }

            // 4. 纯数字过滤
            if (isPureNumber(word)) {
                continue;
            }

            // 5. 转小写统一
            word = word.toLowerCase();
            resultSet.add(word);
        }

        List<String> result = new ArrayList<>(resultSet);
        logger.debug("分词结果（词性筛选）：输入长度={}, 输出词数={}, 词性={}", 
                     text.length(), result.size(), keepNatures);
        return result;
    }

    /**
     * 判断是否为纯数字
     */
    private boolean isPureNumber(String word) {
        if (word.isEmpty()) {
            return true;
        }
        boolean hasDigit = false;
        for (char c : word.toCharArray()) {
            if (Character.isDigit(c) || c == '.' || c == ',') {
                hasDigit = true;
                continue;
            }
            if (!Character.isLetter(c)) {
                return false;
            }
        }
        // 如果全是数字和标点，但没有字母，则认为是纯数字
        return hasDigit && !word.chars().anyMatch(Character::isLetter);
    }

    /**
     * 设置停用词管理器（用于非 Spring 环境）
     */
    public void setStopWordManager(StopWordManager stopWordManager) {
        this.stopWordManager = stopWordManager;
    }
}
