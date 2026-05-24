package com.searchengine.common;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * 文件操作工具类
 * 负责网页原始内容文件和倒排索引文件的读写操作
 * 使用NIO FileChannel实现高效文件操作
 */
public class FileUtil {

    /**
     * 将网页原始内容写入文件
     * 文件格式：[4字节URL长度][URL字节][4字节内容长度][HTML内容字节]
     *
     * @param filePath 文件路径
     * @param url      网页URL
     * @param content  网页HTML内容
     * @return 写入的偏移量（用于后续定位）
     */
    public static long appendDocRaw(String filePath, String url, String content) throws IOException {
        byte[] urlBytes = url.getBytes(StandardCharsets.UTF_8);
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        try (RandomAccessFile raf = new RandomAccessFile(filePath, "rw");
             FileChannel channel = raf.getChannel()) {

            // 定位到文件末尾
            long offset = channel.size();
            channel.position(offset);

            ByteBuffer buffer = ByteBuffer.allocate(4 + urlBytes.length + 4 + contentBytes.length);
            buffer.putInt(urlBytes.length);
            buffer.put(urlBytes);
            buffer.putInt(contentBytes.length);
            buffer.put(contentBytes);
            buffer.flip();

            channel.write(buffer);
            return offset;
        }
    }

    /**
     * 从网页原始内容文件中读取指定偏移量的文档
     *
     * @param filePath 文件路径
     * @param offset   偏移量
     * @return [url, content]
     */
    public static String[] readDocRaw(String filePath, long offset) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r");
             FileChannel channel = raf.getChannel()) {

            channel.position(offset);

            // 读取URL长度
            ByteBuffer lenBuffer = ByteBuffer.allocate(4);
            channel.read(lenBuffer);
            lenBuffer.flip();
            int urlLen = lenBuffer.getInt();

            // 读取URL
            ByteBuffer urlBuffer = ByteBuffer.allocate(urlLen);
            channel.read(urlBuffer);
            urlBuffer.flip();
            String url = StandardCharsets.UTF_8.decode(urlBuffer).toString();

            // 读取内容长度
            ByteBuffer contentLenBuffer = ByteBuffer.allocate(4);
            channel.read(contentLenBuffer);
            contentLenBuffer.flip();
            int contentLen = contentLenBuffer.getInt();

            // 读取内容
            ByteBuffer contentBuffer = ByteBuffer.allocate(contentLen);
            channel.read(contentBuffer);
            contentBuffer.flip();
            String content = StandardCharsets.UTF_8.decode(contentBuffer).toString();

            return new String[]{url, content};
        }
    }

    /**
     * 向倒排索引文件追加写入一个单词的倒排列表
     * 格式：[4字节term_id][4字节文档数量][文档编号列表，每个4字节]
     *
     * @param filePath  文件路径
     * @param termId    单词编号
     * @param docIdList 文档编号列表
     * @return 写入的偏移量和长度 [offset, length]
     */
    public static long[] appendInvertedIndex(String filePath, int termId, int[] docIdList) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "rw");
             FileChannel channel = raf.getChannel()) {

            long offset = channel.size();
            channel.position(offset);

            int totalSize = 4 + 4 + docIdList.length * 4;
            ByteBuffer buffer = ByteBuffer.allocate(totalSize);
            buffer.putInt(termId);
            buffer.putInt(docIdList.length);
            for (int docId : docIdList) {
                buffer.putInt(docId);
            }
            buffer.flip();

            channel.write(buffer);
            return new long[]{offset, totalSize};
        }
    }

    /**
     * 从倒排索引文件中读取指定偏移量的倒排列表
     *
     * @param filePath 文件路径
     * @param offset   偏移量
     * @param length   数据长度
     * @return 文档编号列表
     */
    public static int[] readInvertedIndex(String filePath, long offset, int length) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r");
             FileChannel channel = raf.getChannel()) {

            channel.position(offset);

            ByteBuffer buffer = ByteBuffer.allocate(length);
            channel.read(buffer);
            buffer.flip();

            int termId = buffer.getInt();
            int count = buffer.getInt();
            int[] docIds = new int[count];
            for (int i = 0; i < count; i++) {
                docIds[i] = buffer.getInt();
            }
            return docIds;
        }
    }

    /**
     * 获取文件大小
     */
    public static long getFileSize(String filePath) {
        File file = new File(filePath);
        return file.exists() ? file.length() : 0;
    }

    /**
     * 确保目录存在
     */
    public static void ensureDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private FileUtil() {
    }
}