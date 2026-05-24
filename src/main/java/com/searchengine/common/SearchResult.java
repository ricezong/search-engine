package com.searchengine.common;

/**
 * 搜索结果条目
 */
public class SearchResult implements Comparable<SearchResult> {

    private final int docId;
    private final String url;
    private final String title;
    private final String snippet;
    private final int matchCount;

    public SearchResult(int docId, String url, String title, String snippet, int matchCount) {
        this.docId = docId;
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.matchCount = matchCount;
    }

    public int getDocId() {
        return docId;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getSnippet() {
        return snippet;
    }

    public int getMatchCount() {
        return matchCount;
    }

    @Override
    public int compareTo(SearchResult other) {
        // 按匹配次数降序排列
        return Integer.compare(other.matchCount, this.matchCount);
    }

    @Override
    public String toString() {
        return String.format("SearchResult{docId=%d, url='%s', title='%s', matchCount=%d}",
                docId, url, title, matchCount);
    }
}