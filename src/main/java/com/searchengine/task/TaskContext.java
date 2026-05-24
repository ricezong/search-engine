package com.searchengine.task;

import com.searchengine.analyzer.AnalyzerEngine;
import com.searchengine.common.Config;
import com.searchengine.common.DatabaseManager;
import com.searchengine.crawler.BloomFilterManager;
import com.searchengine.crawler.CrawlerEngine;
import com.searchengine.crawler.DocStorageManager;
import com.searchengine.crawler.PageDownloader;
import com.searchengine.crawler.UrlQueueManager;
import com.searchengine.indexer.IndexerEngine;
import com.searchengine.searcher.SearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 任务上下文
 * 封装每个任务运行时的所有资源，实现数据隔离
 * 
 * 每个TaskContext拥有独立的：
 * - SQLite数据库
 * - 布隆过滤器
 * - URL队列管理器
 * - 文档存储管理器
 * 
 * 共享组件（无状态，线程安全）：
 * - PageDownloader
 * - AnalyzerEngine（内部的HtmlExtractor和Tokenizer）
 */
public class TaskContext {

    private static final Logger logger = LoggerFactory.getLogger(TaskContext.class);

    private final CrawlTask task;
    private final DatabaseManager databaseManager;
    private final BloomFilterManager bloomFilter;
    
    // 共享组件（无状态）
    private final PageDownloader pageDownloader;
    
    // 懒加载组件
    private volatile UrlQueueManager urlQueue;
    private volatile DocStorageManager docStorage;
    private volatile CrawlerEngine crawlerEngine;
    private volatile AnalyzerEngine analyzerEngine;
    private volatile IndexerEngine indexerEngine;
    private volatile SearchEngine searchEngine;

    /**
     * 创建任务上下文
     *
     * @param task           任务实体
     * @param databaseManager 数据库管理器
     * @param bloomFilter    布隆过滤器管理器
     * @param pageDownloader 页面下载器（共享）
     */
    public TaskContext(CrawlTask task, DatabaseManager databaseManager,
                      BloomFilterManager bloomFilter, PageDownloader pageDownloader) {
        this.task = task;
        this.databaseManager = databaseManager;
        this.bloomFilter = bloomFilter;
        this.pageDownloader = pageDownloader;
        
        logger.info("创建任务上下文: taskId={}", task.getTaskId());
    }

    /**
     * 获取URL队列管理器
     */
    public UrlQueueManager getUrlQueue() {
        if (urlQueue == null) {
            synchronized (this) {
                if (urlQueue == null) {
                    try {
                        urlQueue = new UrlQueueManager(databaseManager.getConnection());
                    } catch (SQLException e) {
                        throw new RuntimeException("获取URL队列管理器失败", e);
                    }
                }
            }
        }
        return urlQueue;
    }

    /**
     * 获取文档存储管理器
     */
    public DocStorageManager getDocStorage() {
        if (docStorage == null) {
            synchronized (this) {
                if (docStorage == null) {
                    try {
                        docStorage = new DocStorageManager(
                            databaseManager.getConnection(),
                            Config.getTaskDocRawDir(task.getTaskId())
                        );
                    } catch (SQLException e) {
                        throw new RuntimeException("获取文档存储管理器失败", e);
                    }
                }
            }
        }
        return docStorage;
    }

    /**
     * 获取爬虫引擎
     */
    public CrawlerEngine getCrawlerEngine() {
        if (crawlerEngine == null) {
            synchronized (this) {
                if (crawlerEngine == null) {
                    crawlerEngine = new CrawlerEngine(getUrlQueue(), bloomFilter, getDocStorage(), pageDownloader);
                }
            }
        }
        return crawlerEngine;
    }

    /**
     * 获取分析引擎
     */
    public AnalyzerEngine getAnalyzerEngine() {
        if (analyzerEngine == null) {
            synchronized (this) {
                if (analyzerEngine == null) {
                    try {
                        analyzerEngine = new AnalyzerEngine(databaseManager.getConnection());
                    } catch (SQLException e) {
                        throw new RuntimeException("获取分析引擎失败", e);
                    }
                }
            }
        }
        return analyzerEngine;
    }

    /**
     * 获取索引引擎
     */
    public IndexerEngine getIndexerEngine() {
        if (indexerEngine == null) {
            synchronized (this) {
                if (indexerEngine == null) {
                    try {
                        indexerEngine = new IndexerEngine(
                            databaseManager.getConnection(),
                            Config.getTaskInvertedIndexFile(task.getTaskId())
                        );
                    } catch (SQLException e) {
                        throw new RuntimeException("获取索引引擎失败", e);
                    }
                }
            }
        }
        return indexerEngine;
    }

    /**
     * 获取搜索引擎
     */
    public SearchEngine getSearchEngine() {
        if (searchEngine == null) {
            synchronized (this) {
                if (searchEngine == null) {
                    try {
                        searchEngine = new SearchEngine(
                            databaseManager.getConnection(),
                            Config.getTaskInvertedIndexFile(task.getTaskId())
                        );
                    } catch (SQLException e) {
                        throw new RuntimeException("获取搜索引擎失败", e);
                    }
                }
            }
        }
        return searchEngine;
    }

    /**
     * 获取CrawlTask实体
     */
    public CrawlTask getTask() {
        return task;
    }

    /**
     * 获取任务ID
     */
    public String getTaskId() {
        return task.getTaskId();
    }

    /**
     * 获取布隆过滤器管理器
     */
    public BloomFilterManager getBloomFilter() {
        return bloomFilter;
    }

    /**
     * 释放所有资源
     */
    public void close() {
        logger.info("关闭任务上下文: taskId={}", task.getTaskId());
        
        // 持久化布隆过滤器
        bloomFilter.persist();
        
        // 关闭数据库连接
        databaseManager.close();
    }
}