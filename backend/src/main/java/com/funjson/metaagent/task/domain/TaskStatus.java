package com.funjson.metaagent.task.domain;

/**
 * Task 生命周期状态。
 */
public enum TaskStatus {
    CREATED,
    READY,
    RUNNING,
    WAITING_HUMAN,
    WAITING_APPROVAL,
    BLOCKED,
    SUSPENDED,
    COMPLETED,
    FAILED,
    CANCELLED,
    STALE
}
