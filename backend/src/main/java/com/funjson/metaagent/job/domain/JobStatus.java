package com.funjson.metaagent.job.domain;

/**
 * Job 生命周期状态。
 */
public enum JobStatus {
    CREATED,
    RUNNING,
    WAITING_APPROVAL,
    WAITING_HUMAN,
    SUSPENDED,
    COMPLETED,
    FAILED,
    CANCELLED
}
