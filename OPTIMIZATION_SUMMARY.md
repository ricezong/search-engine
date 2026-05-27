# 搜索引擎文本处理优化方案

## 概述
本次优化将项目的文本处理流程（HTML 清洗、分词、关键词提取）全面升级到业界主流方案。

## 优化内容

### 1. HTML 内容抽取 (`HtmlExtractor.java`)
**原有方案：**
- 使用 Jsoup 解析 HTML
- 移除 script、style 等标签
- 简单文本清理

**优化后：**
- 保持 Jsoup 方案（已是业界标准）
- 无需修改，现有实现已经足够健壮

### 2. 停用词管理 (`StopWordManager.java` - 新增)
**新增功能：**
- 从 `classpath:stopwords.txt` 加载停用词表
- 包含 600+ 中文停用词和英文停用词
- 支持动态添加/删除停用词
- 默认内置常用停用词作为兜底

**停用词分类：**
- 语气助词：的、了、和、是、就、都等
- 代词：我、你、他、这、那等
- 介词：在、于、关于、通过等
- 连词：和、与、及、但是、虽然等
- 副词：很、非常、最、太、更等
- 数量词：一些、所有、每个、任何等
- 标点符号：全角标点
- 英文停用词：a, an, the, is, are 等

### 3. 中文分词器 (`Tokenizer.java` - 优化)
**原有方案：**
- 使用 HanLP 分词
- 简单长度过滤和数字过滤
- 手动判断标点符号

**优化后：**
- **集成停用词过滤**：自动过滤无意义高频词
- **词性筛选支持**：新增 `tokenizeWithNature()` 方法，可指定保留名词 (n)、动词 (v)、形容词 (a) 等
- **改进数字过滤**：更准确地识别纯数字和混合内容
- **最大长度限制**：过滤过长的异常词 (>50 字符)
- **使用 HashSet 去重**：提升性能

**代码示例：**
```java
// 普通分词（带停用词过滤）
List<String> terms = tokenizer.tokenize(text);

// 只保留名词和动词
List<String> keepNatures = Arrays.asList("n", "v");
List<String> terms = tokenizer.tokenizeWithNature(text, keepNatures);
```

### 4. 关键词提取器 (`KeywordExtractor.java` - 新增)
**新增三种业界主流算法：**

#### 4.1 TF-IDF (Term Frequency-Inverse Document Frequency)
- **原理**：词频越高、文档频率越低的词越重要
- **公式**：TF-IDF = (termFreq / totalTerms) × log(totalDocs / docFreq)
- **适用场景**：有完整文档集合的场景
- **优点**：计算简单，效果好，业界标准

#### 4.2 TextRank
- **原理**：基于图的排序算法，借鉴 PageRank 思想
- **实现**：构建词语共现图（窗口大小=5），迭代计算权重
- **适用场景**：单文档关键词提取，无需训练数据
- **优点**：无监督，不依赖语料库

#### 4.3 位置权重增强的 TF-IDF
- **原理**：在 TF-IDF 基础上考虑词语位置
- **权重策略**：
  - 标题中的词 × 2.0
  - 文档开头 10% 的词 × 1.5
- **适用场景**：网页等有明确结构的文档
- **优点**：更符合人类阅读习惯

**使用示例：**
```java
// TF-IDF 模式
List<KeywordResult> keywords = keywordExtractor.extractByTFIDF(
    text, terms, docCount, termDocFreq, 10);

// TextRank 模式
List<KeywordResult> keywords = keywordExtractor.extractByTextRank(text, terms, 10);

// 位置权重 TF-IDF
List<KeywordResult> keywords = keywordExtractor.extractByPositionWeightedTFIDF(
    title, text, terms, docCount, termDocFreq, 10);
```

### 5. 分析引擎 (`AnalyzerEngine.java` - 优化)
**原有方案：**
- 简单的文档频率 (DF) 统计
- 仅记录词频，无关键词权重

**优化后：**
- **支持多种关键词提取模式**：通过 `KeywordMode` 枚举选择
  - `TFIDF`（默认）
  - `TEXTRANK`
  - `POSITION_TFIDF`
- **集成停用词管理器**：分词时自动过滤
- **存储关键词**：在 `doc_meta` 表增加 `keywords` 字段
- **实时计算 IDF**：根据当前文档集合动态计算

**创建方式：**
```java
// 默认 TF-IDF 模式
AnalyzerEngine engine = new AnalyzerEngine(conn);

// 指定 TextRank 模式
AnalyzerEngine engine = new AnalyzerEngine(conn, AnalyzerEngine.KeywordMode.TEXTRANK);
```

### 6. 数据库 schema (`DatabaseManager.java` - 更新)
**变更：**
```sql
-- 原 schema
CREATE TABLE doc_meta (
    doc_id INTEGER PRIMARY KEY,
    snippet TEXT,
    word_count INTEGER DEFAULT 0
);

-- 新 schema
CREATE TABLE doc_meta (
    doc_id INTEGER PRIMARY KEY,
    snippet TEXT,
    word_count INTEGER DEFAULT 0,
    keywords TEXT  -- 新增字段，存储逗号分隔的关键词
);
```

## 文件清单

### 新增文件
1. `/src/main/java/com/searchengine/analyzer/StopWordManager.java` - 停用词管理器
2. `/src/main/java/com/searchengine/analyzer/KeywordExtractor.java` - 关键词提取器
3. `/src/main/resources/stopwords.txt` - 停用词表（600+ 词）

### 修改文件
1. `/src/main/java/com/searchengine/analyzer/Tokenizer.java` - 集成停用词过滤和词性筛选
2. `/src/main/java/com/searchengine/analyzer/AnalyzerEngine.java` - 支持多种关键词提取模式
3. `/src/main/java/com/searchengine/common/DatabaseManager.java` - 增加 keywords 字段

## 对比总结

| 模块 | 原方案 | 优化后 | 业界对标 |
|------|--------|--------|----------|
| 停用词 | 无 | 600+ 中英文停用词 | 百度、谷歌 |
| 分词过滤 | 长度、数字 | + 停用词、词性筛选 | 搜狗、知乎 |
| 关键词算法 | 简单 DF | TF-IDF、TextRank、位置权重 | Elasticsearch、Solr |
| 关键词存储 | 无 | 存入数据库 | 主流搜索引擎 |

## 后续建议

1. **性能优化**：对于大规模文档集，可考虑：
   - 批量计算 IDF 并缓存
   - 使用 Redis 缓存热词
   - 异步处理关键词提取

2. **算法增强**：
   - 集成 HanLP 自带的 TextRank 实现
   - 尝试 BM25 算法替代 TF-IDF
   - 引入命名实体识别 (NER) 提取专有名词

3. **个性化配置**：
   - 支持不同领域自定义停用词
   - 允许调整 TextRank 迭代次数和窗口大小
   - 提供关键词数量可配置

4. **质量评估**：
   - 建立关键词提取评估数据集
   - 对比不同算法效果
   - A/B 测试搜索质量
