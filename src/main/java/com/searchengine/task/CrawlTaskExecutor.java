package com.searchengine.task;

import com.searchengine.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务执行器
 * 使用固定大小线程池管理任务执行，避免无限创建线程
 */
@Component
public class CrawlTaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(CrawlTaskExecutor.class);

    private final TaskManager taskManager;
    private final ExecutorService executor;
    private final AtomicInteger runningCount = new AtomicInteger(0);

    public CrawlTaskExecutor(TaskManager taskManager) {
        this.taskManager = taskManager;
        this.executor = Executors.newFixedThreadPool(Config.MAX_CONCURRENT_TASKS);
        logger.info("任务执行器初始化，最大并发任务数: {}", Config.MAX_CONCURRENT_TASKS);
    }

    /**
     * 检查是否可以提交新任务
     */
    public boolean canSubmit() {
        return runningCount.get() < Config.MAX_CONCURRENT_TASKS;
    }

    /**
     * 提交爬取任务
     */
    public CompletableFuture<Void> executeCrawl(String taskId) {
        return submitTask(taskId, "crawl", () -> {
            TaskContext ctx = taskManager.getTaskContext(taskId);
            CrawlTask task = ctx.getTask();
            try {
                taskManager.updateTaskStatus(taskId, TaskStatus.RUNNING, null);
                // 设置代理配置
                ctx.getCrawlerEngine().setProxyConfig(task.getProxyHost(), task.getProxyPort(), task.isUseProxy());
                ctx.getCrawlerEngine().start(task.getSeedUrls(), task.getMaxPages());
                // 更新进度
                int crawledCount = ctx.getDocStorage().getTotalDocCount();
                task.setCrawledCount(crawledCount);
                taskManager.updateTaskProgress(taskId, crawledCount, 0, 0);

                if (crawledCount == 0) {
                    taskManager.updateTaskStatus(taskId, TaskStatus.FAILED, "爬取失败：未获取到任何网页内容");
                } else {
                    taskManager.updateTaskStatus(taskId, TaskStatus.COMPLETED, null);
                }
            } catch (Exception e) {
                logger.error("爬取任务执行失败: taskId={}", taskId, e);
                taskManager.updateTaskStatus(taskId, TaskStatus.FAILED, e.getMessage());
            }
        });
    }

    /**
     * 提交分析任务
     */
    public CompletableFuture<Void> executeAnalyze(String taskId) {
        return submitTask(taskId, "analyze", () -> {
            TaskContext ctx = taskManager.getTaskContext(taskId);
            CrawlTask task = ctx.getTask();
            try {
                taskManager.updateTaskStatus(taskId, TaskStatus.RUNNING, null);
                int analyzedCount = ctx.getAnalyzerEngine().analyze();
                task.setAnalyzedCount(analyzedCount);
                taskManager.updateTaskProgress(taskId, task.getCrawledCount(), analyzedCount, 0);
                taskManager.updateTaskStatus(taskId, TaskStatus.COMPLETED, null);
            } catch (Exception e) {
                logger.error("分析任务执行失败: taskId={}", taskId, e);
                taskManager.updateTaskStatus(taskId, TaskStatus.FAILED, e.getMessage());
            }
        });
    }

    /**
     * 提交索引构建任务
     */
    public CompletableFuture<Void> executeIndex(String taskId) {
        return submitTask(taskId, "index", () -> {
            TaskContext ctx = taskManager.getTaskContext(taskId);
            CrawlTask task = ctx.getTask();
            try {
                taskManager.updateTaskStatus(taskId, TaskStatus.RUNNING, null);
                ctx.getIndexerEngine().build();
                int termCount = ctx.getIndexerEngine().getTermCount();
                task.setIndexedTermCount(termCount);
                taskManager.updateTaskProgress(taskId, task.getCrawledCount(), task.getAnalyzedCount(), termCount);
                taskManager.updateTaskStatus(taskId, TaskStatus.COMPLETED, null);
            } catch (Exception e) {
                logger.error("索引构建任务执行失败: taskId={}", taskId, e);
                taskManager.updateTaskStatus(taskId, TaskStatus.FAILED, e.getMessage());
            }
        });
    }

    /**
     * 提交全流程任务（爬取 -> 分析 -> 索引）
     */
    public CompletableFuture<Void> executeFull(String taskId) {
        return submitTask(taskId, "full", () -> {
            TaskContext ctx = taskManager.getTaskContext(taskId);
            CrawlTask task = ctx.getTask();
            try {
                taskManager.updateTaskStatus(taskId, TaskStatus.RUNNING, null);

                // 设置代理配置
                ctx.getCrawlerEngine().setProxyConfig(task.getProxyHost(), task.getProxyPort(), task.isUseProxy());

                // 1. 爬取
                logger.info("任务 {} 开始爬取阶段", taskId);
                ctx.getCrawlerEngine().start(task.getSeedUrls(), task.getMaxPages());
                int crawledCount = ctx.getDocStorage().getTotalDocCount();
                task.setCrawledCount(crawledCount);
                taskManager.updateTaskProgress(taskId, crawledCount, 0, 0);

                // 检查是否爬取到内容
                if (crawledCount == 0) {
                    taskManager.updateTaskStatus(taskId, TaskStatus.FAILED, "爬取失败：未获取到任何网页内容，请检查种子URL或网络连接");
                    logger.warn("任务 {} 爬取0页，标记为失败", taskId);
                    return;
                }

                // 2. 分析
                logger.info("任务 {} 开始分析阶段，共{}页", taskId, crawledCount);
                int analyzedCount = ctx.getAnalyzerEngine().analyze();
                task.setAnalyzedCount(analyzedCount);
                taskManager.updateTaskProgress(taskId, crawledCount, analyzedCount, 0);

                // 3. 索引
                logger.info("任务 {} 开始索引阶段", taskId);
                ctx.getIndexerEngine().build();
                int termCount = ctx.getIndexerEngine().getTermCount();
                task.setIndexedTermCount(termCount);
                taskManager.updateTaskProgress(taskId, crawledCount, analyzedCount, termCount);

                taskManager.updateTaskStatus(taskId, TaskStatus.COMPLETED, null);
                logger.info("任务 {} 全流程执行完成: 爬取{}页, 分析{}页, 索引{}词", taskId, crawledCount, analyzedCount, termCount);

            } catch (Exception e) {
                logger.error("全流程任务执行失败: taskId={}", taskId, e);
                taskManager.updateTaskStatus(taskId, TaskStatus.FAILED, e.getMessage());
            }
        });
    }

    /**
     * 停止任务
     */
    public void stopTask(String taskId) {
        TaskContext ctx = taskManager.getTaskContext(taskId);
        ctx.getCrawlerEngine().stop();
        taskManager.updateTaskStatus(taskId, TaskStatus.STOPPED, null);
    }

    /**
     * 提交任务到线程池
     */
    private CompletableFuture<Void> submitTask(String taskId, String action, Runnable task) {
        if (!canSubmit()) {
            logger.warn("任务队列已满，无法提交新任务: taskId={}", taskId);
            throw new RuntimeException("任务队列已满，当前最大并发任务数: " + Config.MAX_CONCURRENT_TASKS);
        }

        runningCount.incrementAndGet();
        logger.info("提交任务: taskId={}, action={}", taskId, action);

        return CompletableFuture.runAsync(task, executor).whenComplete((result, ex) -> {
            runningCount.decrementAndGet();
            if (ex != null) {
                logger.error("任务执行异常: taskId={}, action={}", taskId, action, ex);
            }
            logger.info("任务完成: taskId={}, action={}", taskId, action);
        });
    }

    /**
     * 获取当前运行任务数
     */
    public int getRunningCount() {
        return runningCount.get();
    }
}