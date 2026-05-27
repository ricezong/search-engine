package com.searchengine.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite数据库管理器
 * 负责数据库连接、初始化表结构
 * 使用WAL模式提升并发写入性能
 *
 * 不再使用单例模式，每个任务创建独立的DatabaseManager实例
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private final String dbFile;
    private final String dataDir;
    private final String docRawDir;
    private volatile Connection connection;

    /**
     * 创建数据库管理器
     *
     * @param dbFile    SQLite数据库文件路径
     * @param dataDir   数据目录
     * @param docRawDir 网页原始内容存储目录
     */
    public DatabaseManager(String dbFile, String dataDir, String docRawDir) {
        this.dbFile = dbFile;
        this.dataDir = dataDir;
        this.docRawDir = docRawDir;
        initDatabase();
    }

    /**
     * 初始化数据库：创建目录、加载驱动、建表
     */
    private void initDatabase() {
        try {
            // 确保数据目录存在
            Path dataPath = Path.of(dataDir);
            if (!Files.exists(dataPath)) {
                Files.createDirectories(dataPath);
                logger.info("创建数据目录: {}", dataPath.toAbsolutePath());
            }

            // 确保网页存储目录存在
            Path docRawPath = Path.of(docRawDir);
            if (!Files.exists(docRawPath)) {
                Files.createDirectories(docRawPath);
                logger.info("创建网页存储目录: {}", docRawPath.toAbsolutePath());
            }

            // 加载SQLite JDBC驱动
            Class.forName("org.sqlite.JDBC");
            logger.info("SQLite JDBC驱动加载成功");

            // 创建连接
            String dbUrl = "jdbc:sqlite:" + dbFile;
            connection = DriverManager.getConnection(dbUrl);

            // 启用WAL模式，提升并发性能
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=-64000"); // 64MB缓存
                stmt.execute("PRAGMA temp_store=MEMORY");
            }

            logger.info("SQLite数据库连接成功: {}", dbFile);

            // 初始化表结构
            createTables();

        } catch (Exception e) {
            logger.error("数据库初始化失败: {}", dbFile, e);
            throw new RuntimeException("数据库初始化失败: " + dbFile, e);
        }
    }

    /**
     * 创建所有业务表
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // 1. URL队列表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS url_queue (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    url TEXT NOT NULL UNIQUE,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    depth INTEGER NOT NULL DEFAULT 0,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_url_queue_status ON url_queue(status)");
            logger.info("创建表: url_queue");

            // 2. 文档编号映射表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS doc_id_map (
                    doc_id INTEGER PRIMARY KEY,
                    url TEXT NOT NULL UNIQUE,
                    title TEXT,
                    file_path TEXT,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_doc_id_map_url ON doc_id_map(url)");
            logger.info("创建表: doc_id_map");

            // 3. 单词编号映射表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS term_id_map (
                    term_id INTEGER PRIMARY KEY,
                    term TEXT NOT NULL UNIQUE
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_term_id_map_term ON term_id_map(term)");
            logger.info("创建表: term_id_map");

            // 4. 临时索引表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS temp_index (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    term_id INTEGER NOT NULL,
                    doc_id INTEGER NOT NULL,
                    FOREIGN KEY (term_id) REFERENCES term_id_map(term_id),
                    FOREIGN KEY (doc_id) REFERENCES doc_id_map(doc_id)
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_temp_index_term ON temp_index(term_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_temp_index_doc ON temp_index(doc_id)");
            logger.info("创建表: temp_index");

            // 5. 单词偏移量表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS term_offset (
                    term_id INTEGER PRIMARY KEY,
                    offset_val BIGINT NOT NULL,
                    length INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (term_id) REFERENCES term_id_map(term_id)
                )
            """);
            logger.info("创建表: term_offset");

            // 6. 文档元信息表（增加 keywords 字段）
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS doc_meta (
                    doc_id INTEGER PRIMARY KEY,
                    snippet TEXT,
                    word_count INTEGER DEFAULT 0,
                    keywords TEXT,
                    FOREIGN KEY (doc_id) REFERENCES doc_id_map(doc_id)
                )
            """);
            logger.info("创建表: doc_meta");
        }
    }

    /**
     * 获取数据库连接
     * 如果连接已关闭则重新创建
     * 使用synchronized确保线程安全
     */
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String dbUrl = "jdbc:sqlite:" + dbFile;
            connection = DriverManager.getConnection(dbUrl);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
            }
        }
        return connection;
    }

    /**
     * 获取数据库文件路径
     */
    public String getDbFile() {
        return dbFile;
    }

    /**
     * 关闭数据库连接
     */
    public void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    logger.info("SQLite数据库连接已关闭: {}", dbFile);
                }
            } catch (SQLException e) {
                logger.error("关闭数据库连接失败: {}", dbFile, e);
            }
        }
    }
}