# Mini Search Engine

一个小型中文搜索引擎，采用前后端分离架构，支持多任务并行管理。系统可以爬取网页、分析内容、构建索引，并提供搜索查询能力。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.4.3 + Java 17 |
| 数据库 | SQLite（WAL 模式） |
| 网页下载 | Jsoup 1.17.2 |
| 中文分词 | HanLP portable-1.8.4 |
| URL 去重 | Guava BloomFilter 33.0.0 |
| 前端框架 | Vue 3 + Vite |
| UI 组件 | Element Plus |
| 路由管理 | Vue Router 4 |
| HTTP 客户端 | Axios |

## 项目结构

```
search-engine/
├── pom.xml                          # Maven 项目配置
├── src/main/java/com/searchengine/
│   ├── SearchEngineApplication.java # Spring Boot 启动类
│   │
│   ├── config/
│   │   └── WebConfig.java           # CORS 跨域配置
│   │
│   ├── controller/
│   │   └── SearchEngineController.java  # REST API 控制器
│   │
│   ├── task/                        # 多任务管理层
│   │   ├── TaskStatus.java          # 任务状态枚举
│   │   ├── CrawlTask.java           # 任务实体类
│   │   ├── TaskContext.java         # 任务上下文（per-task 资源容器）
│   │   ├── TaskManager.java         # 任务管理器（master DB + 生命周期）
│   │   └── CrawlTaskExecutor.java   # 任务执行器（固定线程池）
│   │
│   ├── crawler/                     # 爬虫模块
│   │   ├── CrawlerEngine.java       # 爬虫核心引擎（BFS 策略）
│   │   ├── UrlQueueManager.java     # URL 队列管理（SQLite）
│   │   ├── BloomFilterManager.java  # 布隆过滤器（URL 去重）
│   │   ├── DocStorageManager.java   # 网页存储管理（混合存储）
│   │   └── PageDownloader.java      # 网页下载器（Jsoup）
│   │
│   ├── analyzer/                    # 分析模块
│   │   ├── AnalyzerEngine.java      # 分析引擎（文本抽取+分词+建索引）
│   │   ├── HtmlExtractor.java       # HTML 文本抽取（Jsoup）
│   │   └── Tokenizer.java           # 中文分词器（HanLP）
│   │
│   ├── indexer/
│   │   └── IndexerEngine.java       # 索引引擎（构建倒排索引）
│   │
│   ├── searcher/
│   │   └── SearchEngine.java        # 搜索引擎（查询+排序+分页）
│   │
│   └── common/                      # 公共工具
│       ├── Config.java              # 全局配置常量（动态路径生成）
│       ├── DatabaseManager.java     # SQLite 数据库管理器
│       ├── FileUtil.java            # 文件操作工具（NIO）
│       └── SearchResult.java        # 搜索结果实体
│
├── frontend/                        # Vue 3 前端
│   ├── package.json
│   ├── vite.config.js               # Vite 配置（含后端代理）
│   ├── index.html
│   └── src/
│       ├── main.js                  # 入口（注册 ElementPlus + Router）
│       ├── App.vue                  # 根组件（导航栏 + 路由视图）
│       ├── router/index.js          # 路由配置
│       ├── api/index.js             # API 封装层
│       └── views/
│           ├── TaskList.vue         # 任务管理首页
│           ├── TaskDetail.vue       # 任务详情页
│           └── Search.vue           # 搜索页面
│
└── data/                            # 运行时数据（自动生成）
    ├── master.db                    # master 数据库（任务元信息）
    └── tasks/
        └── {taskId}/               # 每个任务独立数据目录
            ├── search_engine.db
            ├── bloom_filter.bin
            ├── index.bin
            └── doc_raw/
```

## 多任务架构设计

每个任务拥有完全独立的数据目录和数据库，互不干扰：

```
TaskManager (Spring @Component, 单例)
├── master.db → 存储所有任务元信息
├── CrawlTaskExecutor → 固定线程池(3个线程)
└── ConcurrentHashMap<String, TaskContext> → 活跃任务缓存

TaskContext (普通类, 由 TaskManager 创建)
├── DatabaseManager → 任务专属 SQLite 连接
├── BloomFilterManager → 任务专属布隆过滤器
├── PageDownloader → 共享（无状态）
├── UrlQueueManager → 懒加载
├── DocStorageManager → 懒加载
├── CrawlerEngine → 懒加载
├── AnalyzerEngine → 懒加载
├── IndexerEngine → 懒加载
└── SearchEngine → 懒加载
```

### Bean 管理策略

| 组件 | 管理方式 | 原因 |
|------|---------|------|
| `TaskManager` | Spring @Component | 单例，管理全局任务状态 |
| `CrawlTaskExecutor` | Spring @Component | 单例，全局线程池 |
| `SearchEngineController` | Spring @Component | 单例，REST API |
| `PageDownloader` | Spring @Component | 无状态，可安全共享 |
| `DatabaseManager` | 普通类（new） | 每任务独立实例 |
| `BloomFilterManager` | 普通类（new） | 每任务独立实例 |
| `UrlQueueManager` | 普通类（new） | 每任务独立实例 |
| `DocStorageManager` | 普通类（new） | 每任务独立实例 |
| `CrawlerEngine` | 普通类（new） | 每任务独立实例 |
| `AnalyzerEngine` | 普通类（new） | 每任务独立实例 |
| `IndexerEngine` | 普通类（new） | 每任务独立实例 |
| `SearchEngine` | 普通类（new） | 每任务独立实例 |

## REST API 接口

### 任务管理

| 方法 | 路径 | 说明 | 请求体 |
|------|------|------|--------|
| POST | `/api/tasks` | 创建任务 | `{taskName, seedUrls[], maxPages}` |
| GET | `/api/tasks` | 获取任务列表 | - |
| GET | `/api/tasks/{taskId}` | 获取任务详情 | - |
| DELETE | `/api/tasks/{taskId}` | 删除任务及数据 | - |

### 任务操作

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/tasks/{taskId}/crawl` | 启动爬取 |
| POST | `/api/tasks/{taskId}/analyze` | 执行分析 |
| POST | `/api/tasks/{taskId}/index` | 构建索引 |
| POST | `/api/tasks/{taskId}/full` | 全流程执行（爬取→分析→索引） |
| POST | `/api/tasks/{taskId}/stop` | 停止任务 |
| GET | `/api/tasks/{taskId}/stats` | 获取统计信息 |
| GET | `/api/tasks/{taskId}/search?query=xxx&page=1` | 在指定任务中搜索 |

## 核心流程

### 爬取流程

```
种子URL → url_queue(PENDING) → poll → CRAWLING → PageDownloader.download()
                                                     ↓
                                              DocStorageManager.storeDocument()
                                                     ↓
                                        写入 doc_raw 文件 + doc_id_map 表
                                                     ↓
                                              addNewUrls() 链接入队
                                                     ↓
                                              markCrawled() → CRAWLED
```

**并发控制机制：**
- `dispatchedCount`：已提交到线程池的任务数
- `completedCount`：已完成的任务数（无论成功失败）
- `emptyPollCount`：连续空轮询计数（超120秒退出）
- `addNewUrls()` 内部 synchronized：保证队列总量 ≤ maxPages

### 分析流程

```
遍历 doc_id_map → 读取 HTML → HtmlExtractor(去标签+提取文本+摘要)
                                       ↓
                               Tokenizer(HanLP分词+过滤)
                                       ↓
                           每个词 → term_id_map + temp_index 表
```

### 索引构建流程

```
SQL: SELECT term_id, GROUP_CONCAT(doc_id) FROM temp_index GROUP BY term_id
                                   ↓
                    写入 index.bin（二进制格式）
                    [4字节term_id][4字节doc数量N][N×4字节doc_id]
                                   ↓
                    记录偏移量到 term_offset 表
```

### 搜索流程

```
用户输入 → HanLP分词 → 查term_id_map缓存 → 获取term_id
                                                  ↓
                            查term_offset → 从index.bin读倒排列表
                                                  ↓
                            统计匹配次数 → 降序排序 → 分页
                                                  ↓
                            查doc_id_map+doc_meta → 补充URL/标题/摘要
```

## 存储方案

### 混合存储策略

| 数据类型 | 存储位置 | 原因 |
|---------|---------|------|
| 任务元信息 | master.db | 全局管理 |
| URL队列 | search_engine.db | 事务操作频繁 |
| 文档编号映射 | search_engine.db | 需要双向查询 |
| 单词编号映射 | search_engine.db | 需要双向查询 |
| 临时索引 | search_engine.db | 需要 GROUP BY 聚合 |
| 偏移量 | search_engine.db | 需要索引查询 |
| 网页原始内容 | doc_raw 文件 | 大对象，顺序写入 |
| 倒排索引 | index.bin 文件 | 二进制，O(1)定位 |
| 布隆过滤器 | bloom_filter.bin | 二进制序列化 |

### 文件格式

**网页原始内容文件（doc_raw_0.bin）：**
```
[4字节URL长度][URL字节][4字节内容长度][HTML内容字节]
```

**倒排索引文件（index.bin）：**
```
[4字节term_id][4字节文档数量N][N×4字节doc_id]
```

## 前端页面

### 任务管理首页（`/`）
- 任务卡片列表：任务名、状态标签、统计数据、种子URL
- 新建任务对话框：任务名称、种子URL、最大页面数
- 操作按钮：全流程、爬取、分析、索引、停止、详情、搜索、删除
- 每5秒自动刷新

### 搜索页面（`/search`）
- 任务选择器 + 搜索框
- 搜索结果：标题、URL、摘要、匹配次数
- 分页支持

### 任务详情页（`/tasks/:id`）
- 基本信息、统计数据、详细统计
- 操作按钮、每3秒自动刷新

## 启动方式

```bash
# 1. 后端（IDEA 中运行 SearchEngineApplication.java）
# 端口: 8080

# 2. 前端
cd frontend
npm install
npm run dev
# 端口: 5173（通过 Vite 代理访问后端 /api → localhost:8080）
```

## 依赖说明

### 后端（pom.xml）

| 依赖 | 版本 | 用途 |
|------|------|------|
| spring-boot-starter-web | 3.4.3 | Web 框架 |
| sqlite-jdbc | 3.45.1.0 | SQLite 驱动 |
| jsoup | 1.17.2 | HTML 解析/网页下载 |
| hanlp | portable-1.8.4 | 中文分词 |
| guava | 33.0.0-jre | 布隆过滤器 |

### 前端（package.json）

| 依赖 | 用途 |
|------|------|
| vue 3 | 前端框架 |
| element-plus | UI 组件库 |
| vue-router 4 | 路由管理 |
| axios | HTTP 客户端 |
| vite | 构建工具 |

## 已知限制

1. **SPA 网站爬取效果有限**：Jsoup 不执行 JavaScript，哔哩哔哩等 SPA 网站只能爬到很少页面
2. **SQLite 并发限制**：单个任务内多线程写入依赖 WAL 模式，高并发下可能有性能瓶颈
3. **内存缓存**：搜索时将 term_id_map 全量加载到内存，数据量极大时可能 OOM
4. **无认证机制**：所有 API 无鉴权，仅适合本地开发使用