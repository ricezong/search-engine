package com.searchengine.analyzer;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import com.searchengine.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文分词器
 * 使用HanLP进行中文分词，替代原方案中基于Trie树的字典分词方法
 * HanLP内置了更完善的词典和分词算法，支持多种分词模式
 */
@Component
public class Tokenizer {

    private static final Logger logger = LoggerFactory.getLogger(Tokenizer.class);

    /**
     * 对文本进行分词
     * 过滤掉标点符号、空白字符和过短的词
     *
     * @param text 待分词文本
     * @return 分词结果列表（已去重）
     */
    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        List<Term> terms = HanLP.segment(text);
        List<String> result = new ArrayList<>();

        for (Term term : terms) {
            String word = term.word.trim();
            // 过滤条件：长度过短、纯数字、标点符号
            if (word.length() < Config.MIN_TERM_LENGTH) {
                continue;
            }
            if (isPunctuation(word)) {
                continue;
            }
            if (isPureNumber(word)) {
                continue;
            }
            // 转小写统一
            word = word.toLowerCase();
            if (!result.contains(word)) {
                result.add(word);
            }
        }

        logger.debug("分词结果: 输入长度={}, 输出词数={}", text.length(), result.size());
        return result;
    }

    /**
     * 判断是否为标点符号
     */
    private boolean isPunctuation(String word) {
        if (word.length() != 1) {
            return false;
        }
        char c = word.charAt(0);
        return Character.isWhitespace(c) ||
                Character.getType(c) == Character.CONNECTOR_PUNCTUATION ||
                Character.getType(c) == Character.DASH_PUNCTUATION ||
                Character.getType(c) == Character.END_PUNCTUATION ||
                Character.getType(c) == Character.FINAL_QUOTE_PUNCTUATION ||
                Character.getType(c) == Character.INITIAL_QUOTE_PUNCTUATION ||
                Character.getType(c) == Character.OTHER_PUNCTUATION ||
                Character.getType(c) == Character.START_PUNCTUATION;
    }

    /**
     * 判断是否为纯数字
     */
    private boolean isPureNumber(String word) {
        for (char c : word.toCharArray()) {
            if (!Character.isDigit(c) && c != '.' && c != ',') {
                return false;
            }
        }
        return true;
    }
}