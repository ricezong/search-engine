package com.searchengine.crawler;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.searchengine.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 布隆过滤器管理器
 * 用于URL去重判断
 * 内存中使用Guava BloomFilter，定期持久化到文件
 *
 * 不再使用@Component注解，由TaskContext按需创建
 */
public class BloomFilterManager {

    private static final Logger logger = LoggerFactory.getLogger(BloomFilterManager.class);

    private BloomFilter<String> bloomFilter;
    private final String filePath;

    /**
     * 创建布隆过滤器管理器
     *
     * @param filePath 布隆过滤器持久化文件路径
     */
    public BloomFilterManager(String filePath) {
        this.filePath = filePath;
        loadOrCreate();
    }

    /**
     * 加载已有布隆过滤器或创建新的
     */
    private void loadOrCreate() {
        File file = new File(filePath);
        if (file.exists() && file.length() > 0) {
            try (InputStream is = new FileInputStream(file)) {
                bloomFilter = BloomFilter.readFrom(is, Funnels.stringFunnel(StandardCharsets.UTF_8));
                logger.info("从文件加载布隆过滤器成功: {}", filePath);
            } catch (IOException e) {
                logger.warn("加载布隆过滤器失败，创建新的: {}", e.getMessage());
                createNew();
            }
        } else {
            createNew();
        }
    }

    /**
     * 创建新的布隆过滤器
     */
    private void createNew() {
        bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                Config.BLOOM_FILTER_EXPECTED_INSERTIONS,
                Config.BLOOM_FILTER_FPP
        );
        logger.info("创建新布隆过滤器，预期插入量={}，误判率={}",
                Config.BLOOM_FILTER_EXPECTED_INSERTIONS, Config.BLOOM_FILTER_FPP);
    }

    /**
     * 判断URL是否可能已存在
     * 返回true表示可能存在（有误判率），false表示一定不存在
     */
    public boolean mightContain(String url) {
        return bloomFilter.mightContain(url);
    }

    /**
     * 将URL添加到布隆过滤器
     * 返回true表示之前不存在（新添加），false表示可能已存在
     */
    public boolean put(String url) {
        return bloomFilter.put(url);
    }

    /**
     * 持久化布隆过滤器到文件
     */
    public void persist() {
        try {
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            try (OutputStream os = new FileOutputStream(filePath)) {
                bloomFilter.writeTo(os);
            }
            logger.info("布隆过滤器已持久化到文件: {}", filePath);
        } catch (IOException e) {
            logger.error("布隆过滤器持久化失败", e);
        }
    }
}