package com.searchengine.task;

/**
 * 任务状态枚举
 */
public enum TaskStatus {
    /** 待执行 */
    PENDING,
    /** 运行中 */
    RUNNING,
    /** 已完成 */
    COMPLETED,
    /** 执行失败 */
    FAILED,
    /** 已停止 */
    STOPPED
}