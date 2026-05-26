package com.searchengine.controller;

import com.searchengine.common.SearchResult;
import com.searchengine.task.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 搜索引擎 REST API 控制器
 * 提供任务管理、爬取、分析、索引、搜索等功能的 HTTP 接口
 */
@RestController
@RequestMapping("/api")
public class SearchEngineController {

    private static final Logger logger = LoggerFactory.getLogger(SearchEngineController.class);

    private final TaskManager taskManager;
    private final CrawlTaskExecutor taskExecutor;

    public SearchEngineController(TaskManager taskManager, CrawlTaskExecutor taskExecutor) {
        this.taskManager = taskManager;
        this.taskExecutor = taskExecutor;
    }

    // ==================== 任务管理 API ====================

    /**
     * 创建任务
     * POST /api/tasks
     * Body: { "taskName": "xxx", "seedUrls": ["..."], "maxPages": 100, "proxyHost": "127.0.0.1", "proxyPort": 7890, "useProxy": true }
     */
    @PostMapping("/tasks")
    public Map<String, Object> createTask(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String taskName = (String) body.getOrDefault("taskName", "未命名任务");
            List<String> seedUrls = (List<String>) body.getOrDefault("seedUrls", Collections.emptyList());
            int maxPages = (int) body.getOrDefault("maxPages", 100);
            String proxyHost = (String) body.getOrDefault("proxyHost", com.searchengine.common.Config.PROXY_HOST);
            int proxyPort = (int) body.getOrDefault("proxyPort", com.searchengine.common.Config.PROXY_PORT);
            boolean useProxy = (boolean) body.getOrDefault("useProxy", true);

            if (seedUrls.isEmpty()) {
                seedUrls = Arrays.asList("https://www.baidu.com", "https://www.sina.com.cn", "https://www.qq.com");
            }

            CrawlTask task = taskManager.createTask(taskName, seedUrls, maxPages, proxyHost, proxyPort, useProxy);
            result.put("status", "success");
            result.put("task", taskToMap(task));
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "创建任务失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 更新任务
     * PUT /api/tasks/{taskId}
     * Body: { "taskName": "xxx", "seedUrls": ["..."], "maxPages": 100, "proxyHost": "127.0.0.1", "proxyPort": 7890, "useProxy": true }
     */
    @PutMapping("/tasks/{taskId}")
    public Map<String, Object> updateTask(@PathVariable String taskId, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            CrawlTask task = taskManager.getTask(taskId);
            if (task == null) {
                result.put("status", "error");
                result.put("message", "任务不存在: " + taskId);
                return result;
            }

            if (task.getStatus() == TaskStatus.RUNNING) {
                result.put("status", "error");
                result.put("message", "运行中的任务不能编辑");
                return result;
            }

            String taskName = (String) body.getOrDefault("taskName", task.getTaskName());
            List<String> seedUrls = (List<String>) body.getOrDefault("seedUrls", task.getSeedUrls());
            int maxPages = (int) body.getOrDefault("maxPages", task.getMaxPages());

            // 代理配置
            String proxyHost = body.containsKey("proxyHost") ? (String) body.get("proxyHost") : null;
            Integer proxyPort = body.containsKey("proxyPort") ? (Integer) body.get("proxyPort") : null;
            Boolean useProxy = body.containsKey("useProxy") ? (Boolean) body.get("useProxy") : null;

            taskManager.updateTask(taskId, taskName, seedUrls, maxPages,
                    proxyHost, proxyPort != null ? proxyPort : 0, useProxy);
            result.put("status", "success");
            result.put("task", taskToMap(taskManager.getTask(taskId)));
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "更新任务失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取任务列表
     * GET /api/tasks
     */
    @GetMapping("/tasks")
    public Map<String, Object> listTasks() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<CrawlTask> tasks = taskManager.listTasks();
            List<Map<String, Object>> taskList = new ArrayList<>();
            for (CrawlTask task : tasks) {
                taskList.add(taskToMap(task));
            }
            result.put("status", "success");
            result.put("tasks", taskList);
            result.put("runningCount", taskExecutor.getRunningCount());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "获取任务列表失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取任务详情
     * GET /api/tasks/{taskId}
     */
    @GetMapping("/tasks/{taskId}")
    public Map<String, Object> getTask(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            CrawlTask task = taskManager.getTask(taskId);
            if (task == null) {
                result.put("status", "error");
                result.put("message", "任务不存在: " + taskId);
                return result;
            }
            result.put("status", "success");
            result.put("task", taskToMap(task));
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "获取任务失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 删除任务
     * DELETE /api/tasks/{taskId}
     */
    @DeleteMapping("/tasks/{taskId}")
    public Map<String, Object> deleteTask(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean deleted = taskManager.deleteTask(taskId);
            if (deleted) {
                result.put("status", "success");
                result.put("message", "任务已删除: " + taskId);
            } else {
                result.put("status", "error");
                result.put("message", "删除任务失败: " + taskId);
            }
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "删除任务失败: " + e.getMessage());
        }
        return result;
    }

    // ==================== 任务操作 API ====================

    /**
     * 启动爬取
     * POST /api/tasks/{taskId}/crawl
     */
    @PostMapping("/tasks/{taskId}/crawl")
    public Map<String, Object> startCrawl(@PathVariable String taskId) {
        return executeTaskAction(taskId, () -> taskExecutor.executeCrawl(taskId), "爬取");
    }

    /**
     * 执行分析
     * POST /api/tasks/{taskId}/analyze
     */
    @PostMapping("/tasks/{taskId}/analyze")
    public Map<String, Object> analyze(@PathVariable String taskId) {
        return executeTaskAction(taskId, () -> taskExecutor.executeAnalyze(taskId), "分析");
    }

    /**
     * 构建索引
     * POST /api/tasks/{taskId}/index
     */
    @PostMapping("/tasks/{taskId}/index")
    public Map<String, Object> buildIndex(@PathVariable String taskId) {
        return executeTaskAction(taskId, () -> taskExecutor.executeIndex(taskId), "索引构建");
    }

    /**
     * 全流程执行
     * POST /api/tasks/{taskId}/full
     */
    @PostMapping("/tasks/{taskId}/full")
    public Map<String, Object> runFull(@PathVariable String taskId) {
        return executeTaskAction(taskId, () -> taskExecutor.executeFull(taskId), "全流程");
    }

    /**
     * 停止任务
     * POST /api/tasks/{taskId}/stop
     */
    @PostMapping("/tasks/{taskId}/stop")
    public Map<String, Object> stopTask(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            taskExecutor.stopTask(taskId);
            result.put("status", "success");
            result.put("message", "任务已停止: " + taskId);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "停止任务失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取任务统计信息
     * GET /api/tasks/{taskId}/stats
     */
    @GetMapping("/tasks/{taskId}/stats")
    public Map<String, Object> getStats(@PathVariable String taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            TaskContext ctx = taskManager.getTaskContext(taskId);
            CrawlTask task = ctx.getTask();

            result.put("status", "success");
            result.put("crawler", ctx.getCrawlerEngine().getStats());
            result.put("analyzer", ctx.getAnalyzerEngine().getStats());
            result.put("indexer", ctx.getIndexerEngine().getStats());
            result.put("crawledCount", task.getCrawledCount());
            result.put("analyzedCount", task.getAnalyzedCount());
            result.put("indexedTermCount", task.getIndexedTermCount());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "获取统计信息失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取任务执行日志
     * GET /api/tasks/{taskId}/logs
     */
    @GetMapping("/tasks/{taskId}/logs")
    public Map<String, Object> getLogs(@PathVariable String taskId,
                                       @RequestParam(defaultValue = "100") int limit) {
        Map<String, Object> result = new HashMap<>();
        try {
            CrawlTask task = taskManager.getTask(taskId);
            if (task == null) {
                result.put("status", "error");
                result.put("message", "任务不存在: " + taskId);
                return result;
            }

            List<Map<String, String>> logs = new ArrayList<>();

            // 添加状态变更日志
            if (task.getCreateTime() > 0) {
                logs.add(Map.of(
                    "time", formatTime(task.getCreateTime()),
                    "level", "INFO",
                    "message", "任务创建: " + task.getTaskName()
                ));
            }

            if (task.getStatus() == TaskStatus.COMPLETED) {
                logs.add(Map.of(
                    "time", formatTime(task.getUpdateTime()),
                    "level", "INFO",
                    "message", "任务完成: 爬取" + task.getCrawledCount() + "页, 分析" + task.getAnalyzedCount() + "页, 索引" + task.getIndexedTermCount() + "词"
                ));
            } else if (task.getStatus() == TaskStatus.FAILED && task.getErrorMessage() != null) {
                logs.add(Map.of(
                    "time", formatTime(task.getUpdateTime()),
                    "level", "ERROR",
                    "message", "任务失败: " + task.getErrorMessage()
                ));
            } else if (task.getStatus() == TaskStatus.STOPPED) {
                logs.add(Map.of(
                    "time", formatTime(task.getUpdateTime()),
                    "level", "WARN",
                    "message", "任务已停止"
                ));
            } else if (task.getStatus() == TaskStatus.RUNNING) {
                logs.add(Map.of(
                    "time", formatTime(System.currentTimeMillis()),
                    "level", "INFO",
                    "message", "运行中: 爬取" + task.getCrawledCount() + "/" + task.getMaxPages() + "页"
                ));
            }

            result.put("status", "success");
            result.put("logs", logs);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "获取日志失败: " + e.getMessage());
        }
        return result;
    }

    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "-";
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(timestamp));
    }

    // ==================== 搜索 API ====================

    /**
     * 在指定任务中搜索
     * GET /api/tasks/{taskId}/search?query=xxx&page=1
     */
    @GetMapping("/tasks/{taskId}/search")
    public Map<String, Object> search(@PathVariable String taskId,
                                      @RequestParam String query,
                                      @RequestParam(defaultValue = "1") int page) {
        Map<String, Object> result = new HashMap<>();
        try {
            TaskContext ctx = taskManager.getTaskContext(taskId);
            List<SearchResult> results = ctx.getSearchEngine().search(query, page);
            int totalCount = ctx.getSearchEngine().getTotalMatchCount(query);

            result.put("status", "success");
            result.put("query", query);
            result.put("page", page);
            result.put("totalCount", totalCount);
            result.put("results", results);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "搜索失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取任务的热门关键词
     * GET /api/tasks/{taskId}/keywords?limit=20
     */
    @GetMapping("/tasks/{taskId}/keywords")
    public Map<String, Object> getKeywords(@PathVariable String taskId,
                                           @RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> result = new HashMap<>();
        try {
            TaskContext ctx = taskManager.getTaskContext(taskId);
            List<String> keywords = ctx.getSearchEngine().getTopKeywords(limit);

            result.put("status", "success");
            result.put("keywords", keywords);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "获取关键词失败: " + e.getMessage());
        }
        return result;
    }

    // ==================== 辅助方法 ====================

    /**
     * 执行任务操作的通用方法
     */
    private Map<String, Object> executeTaskAction(String taskId, Runnable action, String actionName) {
        Map<String, Object> result = new HashMap<>();
        try {
            CrawlTask task = taskManager.getTask(taskId);
            if (task == null) {
                result.put("status", "error");
                result.put("message", "任务不存在: " + taskId);
                return result;
            }

            if (task.getStatus() == TaskStatus.RUNNING) {
                result.put("status", "error");
                result.put("message", "任务正在运行中: " + taskId);
                return result;
            }

            if (!taskExecutor.canSubmit()) {
                result.put("status", "error");
                result.put("message", "任务队列已满，请稍后再试");
                return result;
            }

            action.run();
            result.put("status", "started");
            result.put("message", actionName + "任务已启动");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "启动" + actionName + "失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 将CrawlTask转换为Map
     */
    private Map<String, Object> taskToMap(CrawlTask task) {
        Map<String, Object> map = new HashMap<>();
        map.put("taskId", task.getTaskId());
        map.put("taskName", task.getTaskName());
        map.put("status", task.getStatus().name());
        map.put("seedUrls", task.getSeedUrls());
        map.put("maxPages", task.getMaxPages());
        map.put("crawledCount", task.getCrawledCount());
        map.put("analyzedCount", task.getAnalyzedCount());
        map.put("indexedTermCount", task.getIndexedTermCount());
        map.put("createTime", task.getCreateTime());
        map.put("updateTime", task.getUpdateTime());
        map.put("errorMessage", task.getErrorMessage());
        map.put("proxyHost", task.getProxyHost());
        map.put("proxyPort", task.getProxyPort());
        map.put("useProxy", task.isUseProxy());
        return map;
    }
}