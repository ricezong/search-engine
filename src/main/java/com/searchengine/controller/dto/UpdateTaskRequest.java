package com.searchengine.controller.dto;

import java.util.List;

/**
 * 更新任务请求DTO
 */
public class UpdateTaskRequest {

    private String taskName;
    private List<String> seedUrls;
    private int maxPages;
    private String proxyHost;
    private Integer proxyPort;
    private Boolean useProxy;

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
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

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public Boolean getUseProxy() {
        return useProxy;
    }

    public void setUseProxy(Boolean useProxy) {
        this.useProxy = useProxy;
    }
}