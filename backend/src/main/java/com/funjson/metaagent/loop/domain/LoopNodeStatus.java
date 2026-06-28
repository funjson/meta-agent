package com.funjson.metaagent.loop.domain;

/**
 * LoopNode 生命周期状态。
 */
public enum LoopNodeStatus {
    CREATED,
    READY,
    RUNNING,
    WAITING_CHILDREN,
    WAITING_CHILD_JOB,
    WAITING_TOOL,
    WAITING_HUMAN,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
    BLOCKED,
    ESCALATED
}
