package com.funjson.metaagent.task.domain;

/**
 * TaskRun 生命周期状态。
 */
public enum TaskRunStatus {
    CREATED,
    RUNNING,
    WAITING_CHILD_JOB,
    PAUSED,
    WAITING_HUMAN,
    WAITING_CONFIGURATION,
    COMPLETED,
    FAILED,
    CANCELLED
}
