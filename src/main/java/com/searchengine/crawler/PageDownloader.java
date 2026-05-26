package com.searchengine.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * 网页下载器
 * 使用Jsoup下载网页并解析出链接和标题
 */
@Component
public class PageDownloader {

    private static final Logger logger = LoggerFactory.getLogger(PageDownloader.class);

    /**
     * 下载结果
     */
    public static class DownloadResult {
        private final String url;
        private final String title;
        private final String html;
        private final List<String> links;

        public DownloadResult(String url, String title, String html, List<String> links) {
            this.url = url;
            this.title = title;
            this.html = html;
            this.links = links;
        }

        public String getUrl() {
            return url;
        }

        public String getTitle() {
            return title;
        }

        public String getHtml() {
            return html;
        }

        public List<String> getLinks() {
            return links;
        }
    }

    /**
     * 下载网页并解析（使用默认代理配置）
     *
     * @param url 网页URL
     * @return 下载结果，失败返回null
     */
    public DownloadResult download(String url) {
        return download(url, null, 0, true);
    }

    /**
     * 下载网页并解析（指定代理配置）
     *
     * @param url 网页URL
     * @param proxyHost 代理主机
     * @param proxyPort 代理端口
     * @param useProxy 是否使用代理
     * @return 下载结果，失败返回null
     */
    public DownloadResult download(String url, String proxyHost, int proxyPort, boolean useProxy) {
        try {
            // 检查是否需要使用代理
            boolean shouldUseProxy = useProxy && proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0;

            var connection = Jsoup.connect(url)
                    .userAgent(com.searchengine.common.Config.CRAWLER_USER_AGENT)
                    .timeout(com.searchengine.common.Config.CRAWLER_TIMEOUT_MS)
                    .followRedirects(true)
                    .maxBodySize(5 * 1024 * 1024); // 最大5MB

            if (shouldUseProxy) {
                connection.proxy(proxyHost, proxyPort);
                logger.info("使用代理访问: {}:{}", proxyHost, proxyPort);
            } else {
                logger.info("直连访问: {}", url);
            }

            Document doc = connection.get();

            String title = doc.title();
            if (title == null || title.isBlank()) {
                title = extractTitleFromUrl(url);
            }

            // 提取页面中的所有链接
            List<String> links = extractLinks(doc, url);

            logger.debug("下载网页成功: url={}, title={}, links={}", url, title, links.size());
            return new DownloadResult(url, title, doc.html(), links);

        } catch (Exception e) {
            logger.error("下载网页失败: url={}, error={}", url, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 提取页面中的所有有效链接
     * 只保留http/https链接，过滤掉javascript:、mailto:等
     */
    private List<String> extractLinks(Document doc, String baseUrl) {
        List<String> links = new ArrayList<>();
        Elements hrefs = doc.select("a[href]");

        for (Element a : hrefs) {
            String href = a.attr("abs:href");
            if (href == null || href.isEmpty()) {
                // 尝试相对路径解析
                href = a.attr("href");
                if (href != null && !href.isEmpty() && !href.startsWith("#")) {
                    href = resolveUrl(baseUrl, href);
                }
            }

            if (href != null && isValidUrl(href)) {
                // 去除锚点
                int anchorIndex = href.indexOf('#');
                if (anchorIndex > 0) {
                    href = href.substring(0, anchorIndex);
                }
                // 去除末尾斜杠统一格式
                if (href.endsWith("/")) {
                    href = href.substring(0, href.length() - 1);
                }
                if (!href.isEmpty() && !links.contains(href)) {
                    links.add(href);
                }
            }
        }
        return links;
    }

    /**
     * 解析相对URL
     */
    private String resolveUrl(String baseUrl, String relativeUrl) {
        try {
            URI baseUri = new URI(baseUrl);
            URI resolved = baseUri.resolve(relativeUrl);
            return resolved.normalize().toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * 判断URL是否有效（仅http/https）
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * 从URL中提取标题作为后备
     * 返回完整URL，而非仅host
     */
    private String extractTitleFromUrl(String url) {
        return url != null ? url : "";
    }

}