package com.searchengine.analyzer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HTML内容抽取器
 * 从网页HTML中提取纯文本内容
 * 替代原方案中基于AC自动机的标签去除方法
 * 使用Jsoup进行HTML解析，更加健壮
 */
@Component
public class HtmlExtractor {

    private static final Logger logger = LoggerFactory.getLogger(HtmlExtractor.class);

    /**
     * 抽取结果
     */
    public static class ExtractResult {
        private final String title;
        private final String text;
        private final String snippet;

        public ExtractResult(String title, String text, String snippet) {
            this.title = title;
            this.text = text;
            this.snippet = snippet;
        }

        public String getTitle() {
            return title;
        }

        public String getText() {
            return text;
        }

        public String getSnippet() {
            return snippet;
        }
    }

    /**
     * 从HTML中抽取纯文本
     * 步骤：
     * 1. 去除script、style、option等不可见标签
     * 2. 去除所有HTML标签，保留纯文本
     * 3. 生成摘要（取前200字）
     */
    public ExtractResult extract(String html) {
        if (html == null || html.isBlank()) {
            return new ExtractResult("", "", "");
        }

        try {
            Document doc = Jsoup.parse(html);

            // 去除不可见标签
            doc.select("script, style, option, noscript, iframe, svg").remove();

            // 提取标题
            String title = "";
            if (doc.title() != null && !doc.title().isBlank()) {
                title = doc.title().trim();
            }

            // 提取纯文本
            String text = doc.text();
            if (text == null) {
                text = "";
            }

            // 清理多余空白
            text = text.replaceAll("\\s+", " ").trim();

            // 生成摘要（取前200个字符）
            String snippet = text.length() > 200 ? text.substring(0, 200) + "..." : text;

            return new ExtractResult(title, text, snippet);

        } catch (Exception e) {
            logger.warn("HTML内容抽取失败", e);
            return new ExtractResult("", "", "");
        }
    }
}