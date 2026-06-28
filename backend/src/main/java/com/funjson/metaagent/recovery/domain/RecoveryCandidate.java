package com.funjson.metaagent.recovery.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 从事实表和最新 Checkpoint 重建的恢复候选。
 *
 * @param taskRunId TaskRun ID
 * @param jobId Job ID
 * @param taskId Task ID
 * @param taskRunStatus TaskRun 状态
 * @param leaseOwner 当前租约持有者
 * @param leaseUntil 租约到期时间
 * @param checkpointId Checkpoint ID
 * @param checkpointType Checkpoint 类型
 * @param checkpointRestorable 是否声明可恢复
 * @param checkpointChecksumValid checksum 是否有效
 * @param loopRunId LoopRun ID
 * @param loopRunParentType LoopRun 父类型
 * @param loopNodeId LoopNode ID
 * @param loopNodeStatus LoopNode 状态
 * @param actionType 动作类型
 * @param sideEffectClass 副作用分类
 */
public record RecoveryCandidate(
        UUID taskRunId,
        UUID jobId,
        UUID taskId,
        String taskRunStatus,
        String leaseOwner,
        Instant leaseUntil,
        UUID checkpointId,
        String checkpointType,
        boolean checkpointRestorable,
        boolean checkpointChecksumValid,
        UUID loopRunId,
        String loopRunParentType,
        UUID loopNodeId,
        String loopNodeStatus,
        String actionType,
        String sideEffectClass) {
}
