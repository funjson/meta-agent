package com.funjson.metaagent.loop.domain;

/**
 * LoopRun 生命周期状态。
 */
public enum LoopRunStatus {
    CREATED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
