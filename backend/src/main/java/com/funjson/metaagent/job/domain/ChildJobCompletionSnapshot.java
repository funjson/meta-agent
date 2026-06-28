package com.funjson.metaagent.job.domain;

import java.util.UUID;

/**
 * Child Job 终态回传事务内锁定的派生快照。
 *
 * @param derivationId 派生记录 ID
 * @param parentJobId 父 Job ID
 * @param childJobId 子 Job ID
 * @param originTaskRunId origin TaskRun ID
 * @param originLoopNodeId origin LoopNode ID
 * @param derivationStatus 派生状态
 * @param childJobStatus 子 Job 状态
 */
public record ChildJobCompletionSnapshot(
        UUID derivationId,
        UUID parentJobId,
        UUID childJobId,
        UUID originTaskRunId,
        UUID originLoopNodeId,
        String derivationStatus,
        JobStatus childJobStatus) {
}
