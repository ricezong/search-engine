package com.searchengine.common;

/**
 * 搜索引擎全局配置常量
 * 混合存储方案：SQLite存储索引/元数据，文件存储网页原始内容/倒排索引
 *
 * 路径相关配置通过方法动态生成，支持多任务数据隔离
 */
public class Config {

    // ==================== 数据目录（全局） ====================
    /** 数据根目录 */
    public static final String DATA_DIR = "data";
    /** 任务数据根目录 */
    public static final String TASKS_DIR = DATA_DIR + "/tasks";
    /** Master数据库文件路径（存储任务元信息） */
    public static final String MASTER_DB_FILE = DATA_DIR + "/master.db";

    // ==================== 爬虫配置 ====================
    /** 爬虫线程数 */
    public static final int CRAWLER_THREAD_COUNT = 4;
    /** 爬虫请求间隔（毫秒） */
    public static final int CRAWLER_DELAY_MS = 500;
    /** 爬虫连接超时（毫秒） */
    public static final int CRAWLER_TIMEOUT_MS = 10000;
    /** 爬虫User-Agent */
    public static final String CRAWLER_USER_AGENT = "MiniSearchEngine/1.0";
    /** 最大爬取页面数（0表示不限制） */
    public static final int MAX_CRAWL_PAGES = 10000;
    /** 单个网页存储文件最大大小（1GB） */
    public static final long DOC_RAW_FILE_MAX_SIZE = 1024L * 1024 * 1024;

    // ==================== 代理配置 ====================
    /** 代理主机（mihomo） */
    public static final String PROXY_HOST = "127.0.0.1";
    /** 代理端口（mihomo HTTP代理） */
    public static final int PROXY_PORT = 7890;

    // ==================== 布隆过滤器配置 ====================
    /** 布隆过滤器预期插入量 */
    public static final int BLOOM_FILTER_EXPECTED_INSERTIONS = 10_000_000;
    /** 布隆过滤器误判率 */
    public static final double BLOOM_FILTER_FPP = 0.01;
    /** 布隆过滤器持久化间隔（毫秒） */
    public static final int BLOOM_FILTER_PERSIST_INTERVAL_MS = 30 * 60 * 1000;

    // ==================== 分词配置 ====================
    /** 最短词长度（过滤单字等无意义词） */
    public static final int MIN_TERM_LENGTH = 2;

    // ==================== 查询配置 ====================
    /** 查询结果每页数量 */
    public static final int SEARCH_RESULTS_PER_PAGE = 10;

    // ==================== 任务配置 ====================
    /** 最大并行任务数 */
    public static final int MAX_CONCURRENT_TASKS = 3;

    // ==================== 任务数据路径方法 ====================

    /**
     * 获取指定任务的数据目录
     */
    public static String getTaskDataDir(String taskId) {
        return TASKS_DIR + "/" + taskId;
    }

    /**
     * 获取指定任务的SQLite数据库文件路径
     */
    public static String getTaskDbFile(String taskId) {
        return getTaskDataDir(taskId) + "/search_engine.db";
    }

    /**
     * 获取指定任务的网页原始内容存储目录
     */
    public static String getTaskDocRawDir(String taskId) {
        return getTaskDataDir(taskId) + "/doc_raw";
    }

    /**
     * 获取指定任务的布隆过滤器文件路径
     */
    public static String getTaskBloomFilterFile(String taskId) {
        return getTaskDataDir(taskId) + "/bloom_filter.bin";
    }

    /**
     * 获取指定任务的倒排索引文件路径
     */
    public static String getTaskInvertedIndexFile(String taskId) {
        return getTaskDataDir(taskId) + "/index.bin";
    }

    private Config() {
        // 禁止实例化
    }
}