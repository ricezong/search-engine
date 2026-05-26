package com.searchengine.task;

import com.searchengine.common.Config;

import java.util.List;

/**
 * 爬取任务实体
 * 每个任务拥有独立的数据目录和数据库，实现数据隔离
 */
public class CrawlTask {

    private String taskId;
    private String taskName;
    private TaskStatus status;
    private List<String> seedUrls;
    private int maxPages;
    private int crawledCount;
    private int analyzedCount;
    private int indexedTermCount;
    private long createTime;
    private long updateTime;
    private String errorMessage;
    private String proxyHost;
    private int proxyPort;
    private boolean useProxy;

    public CrawlTask() {
    }

    public CrawlTask(String taskId, String taskName, List<String> seedUrls, int maxPages) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.status = TaskStatus.PENDING;
        this.seedUrls = seedUrls;
        this.maxPages = maxPages;
        this.crawledCount = 0;
        this.analyzedCount = 0;
        this.indexedTermCount = 0;
        this.createTime = System.currentTimeMillis();
        this.updateTime = System.currentTimeMillis();
        // 默认代理配置
        this.proxyHost = Config.PROXY_HOST;
        this.proxyPort = Config.PROXY_PORT;
        this.useProxy = true;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
        this.updateTime = System.currentTimeMillis();
    }

    public List<String> getSeedUrls() {
        return seedUrls;
    }

    public void setSeedUrls(List<String> seedUrls) {
        this.seedUrls = seedUrls;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getCrawledCount() {
        return crawledCount;
    }

    public void setCrawledCount(int crawledCount) {
        this.crawledCount = crawledCount;
    }

    public int getAnalyzedCount() {
        return analyzedCount;
    }

    public void setAnalyzedCount(int analyzedCount) {
        this.analyzedCount = analyzedCount;
    }

    public int getIndexedTermCount() {
        return indexedTermCount;
    }

    public void setIndexedTermCount(int indexedTermCount) {
        this.indexedTermCount = indexedTermCount;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.updateTime = System.currentTimeMillis();
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isUseProxy() {
        return useProxy;
    }

    public void setUseProxy(boolean useProxy) {
        this.useProxy = useProxy;
    }
}