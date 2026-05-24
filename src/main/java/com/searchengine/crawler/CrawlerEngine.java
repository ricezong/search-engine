package com.searchengine.crawler;

import com.searchengine.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 爬虫核心引擎
 * 采用广度优先搜索策略爬取网页
 * 混合存储：URL队列和去重状态存SQLite，网页内容存文件
 *
 * 不再使用@Component注解，由TaskContext按需创建
 */
public class CrawlerEngine {

    private static final Logger logger = LoggerFactory.getLogger(CrawlerEngine.class);

    private final UrlQueueManager urlQueue;
    private final BloomFilterManager bloomFilter;
    private final DocStorageManager docStorage;
    private final PageDownloader downloader;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ExecutorService executor;
    private volatile int maxPages;

    // 已提交的任务计数（poll出并提交到线程池）
    private final AtomicInteger dispatchedCount = new AtomicInteger(0);
    // 已完成的任务计数（processTask执行完毕并markCrawled）
    private final AtomicInteger completedCount = new AtomicInteger(0);

    private long lastBloomFilterPersistTime = 0;

    /**
     * 创建爬虫引擎
     *
     * @param urlQueue    URL队列管理器
     * @param bloomFilter 布隆过滤器管理器
     * @param docStorage  文档存储管理器
     * @param downloader  页面下载器
     */
    public CrawlerEngine(UrlQueueManager urlQueue, BloomFilterManager bloomFilter,
                         DocStorageManager docStorage, PageDownloader downloader) {
        this.urlQueue = urlQueue;
        this.bloomFilter = bloomFilter;
        this.docStorage = docStorage;
        this.downloader = downloader;
    }

    /**
     * 启动爬虫
     *
     * @param seedUrls 种子URL列表
     * @param maxPages 最大爬取页面数
     */
    public void start(List<String> seedUrls, int maxPages) {
        if (running.getAndSet(true)) {
            logger.warn("爬虫已在运行中");
            return;
        }

        this.maxPages = maxPages;
        // 重置计数器
        dispatchedCount.set(0);
        completedCount.set(0);

        logger.info("========== 爬虫启动 ==========");
        logger.info("种子URL: {}", seedUrls);
        logger.info("最大爬取页面数: {}", maxPages);

        // 添加种子URL
        urlQueue.addSeedUrls(seedUrls);
        // 将种子URL也加入布隆过滤器
        for (String url : seedUrls) {
            bloomFilter.put(url);
        }

        lastBloomFilterPersistTime = System.currentTimeMillis();

        try {
            executor = Executors.newFixedThreadPool(Config.CRAWLER_THREAD_COUNT);
            int emptyPollCount = 0;

            while (running.get()) {
                // 已完成数达标 → 退出
                if (maxPages > 0 && completedCount.get() >= maxPages) {
                    logger.info("已完成爬取页面数达到上限: {}", maxPages);
                    break;
                }

                // 已提交数达标 → 不再提交新任务，等待已提交的任务完成
                if (maxPages > 0 && dispatchedCount.get() >= maxPages) {
                    logger.debug("已提交任务数达到上限，等待异步任务完成... 已提交={}, 已完成={}",
                            dispatchedCount.get(), completedCount.get());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                // 从队列取任务
                UrlQueueManager.UrlTask task = urlQueue.poll();
                if (task == null) {
                    // 没有待爬取的任务，检查是否有任务正在爬取中
                    int pendingCount = urlQueue.getPendingCount();
                    int crawlingCount = urlQueue.getCrawlingCount();

                    if (pendingCount == 0 && crawlingCount == 0) {
                        // 真的没有任务了
                        logger.info("URL队列为空且无进行中的任务，爬取完成。已提交={}, 已完成={}",
                                dispatchedCount.get(), completedCount.get());
                        break;
                    }

                    emptyPollCount++;
                    if (emptyPollCount > 120) {
                        logger.warn("等待新URL超时(120秒)，停止爬取。待处理: {}, 爬取中: {}", pendingCount, crawlingCount);
                        break;
                    }

                    logger.debug("暂无待爬取URL，等待异步任务完成... (pending={}, crawling={}, waitCount={})",
                            pendingCount, crawlingCount, emptyPollCount);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                // 成功取到任务，重置空轮询计数
                emptyPollCount = 0;

                // 提交下载任务
                dispatchedCount.incrementAndGet();
                executor.submit(() -> {
                    try {
                        processTask(task);
                    } catch (Exception e) {
                        logger.error("处理任务异常: url={}", task.getUrl(), e);
                        urlQueue.markFailed(task.getId());
                    } finally {
                        // 无论成功还是失败，都+1，避免失败任务导致无限等待
                        completedCount.incrementAndGet();
                    }
                });

                // 控制爬取速率
                try {
                    Thread.sleep(Config.CRAWLER_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                // 定期持久化布隆过滤器
                maybePersistBloomFilter();
            }

            // 等待所有任务完成
            if (executor != null) {
                executor.shutdown();
                if (!executor.awaitTermination(120, TimeUnit.SECONDS)) {
                    logger.warn("线程池未能在120秒内完成所有任务，强制关闭");
                    executor.shutdownNow();
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // 最终持久化布隆过滤器
            bloomFilter.persist();
            running.set(false);
            logger.info("========== 爬虫停止 ==========");
            logger.info("统计: 已提交={}, 已完成={}, 数据库已爬取={}, 待爬取={}, 已存储文档={}",
                    dispatchedCount.get(), completedCount.get(),
                    urlQueue.getCrawledCount(), urlQueue.getPendingCount(),
                    docStorage.getTotalDocCount());
        }
    }

    /**
     * 处理单个爬取任务
     */
    private void processTask(UrlQueueManager.UrlTask task) {
        // 检查运行标志，快速退出
        if (!running.get()) {
            urlQueue.markFailed(task.getId());
            return;
        }

        String url = task.getUrl();
        logger.info("爬取: {} (depth={})", url, task.getDepth());

        // 下载网页
        PageDownloader.DownloadResult result = downloader.download(url);
        if (result == null) {
            urlQueue.markFailed(task.getId());
            return;
        }

        // 再次检查运行标志
        if (!running.get()) {
            urlQueue.markFailed(task.getId());
            return;
        }

        // 存储网页内容
        int docId = docStorage.storeDocument(url, result.getTitle(), result.getHtml());
        if (docId < 0) {
            urlQueue.markFailed(task.getId());
            return;
        }

        // 仅当未达到最大页面数限制时，才提取新链接并加入队列
        // addNewUrls内部synchronized确保不会超量添加
        if (task.getDepth() < 5) {
            int added = urlQueue.addNewUrls(result.getLinks(), task.getDepth() + 1, bloomFilter, maxPages);
            if (added > 0) {
                logger.debug("发现新链接: {}个", added);
            }
        }

        // 标记任务完成（completedCount在executor.submit的finally中递增）
        urlQueue.markCrawled(task.getId());
    }

    /**
     * 定期持久化布隆过滤器
     */
    private void maybePersistBloomFilter() {
        long now = System.currentTimeMillis();
        if (now - lastBloomFilterPersistTime >= Config.BLOOM_FILTER_PERSIST_INTERVAL_MS) {
            bloomFilter.persist();
            lastBloomFilterPersistTime = now;
        }
    }

    /**
     * 停止爬虫
     * 立即中断主循环和线程池中正在执行的任务
     */
    public void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * 检查爬虫是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取爬取统计信息
     */
    public String getStats() {
        return String.format("待爬取: %d, 已爬取: %d, 已存储文档: %d",
                urlQueue.getPendingCount(),
                urlQueue.getCrawledCount(),
                docStorage.getTotalDocCount());
    }
}