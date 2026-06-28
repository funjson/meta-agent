package com.funjson.metaagent.recovery.api;

import java.time.Instant;
import java.util.UUID;

import com.funjson.metaagent.recovery.domain.InterruptionType;
import com.funjson.metaagent.recovery.domain.RecoveryDisposition;

/**
 * TaskRun 恢复检查结果。
 *
 * @param taskRunId TaskRun ID
 * @param checkpointId Checkpoint ID
 * @param checkpointType Checkpoint 类型
 * @param interruptionType 中断类型
 * @param disposition 恢复处置
 * @param decisionCode 稳定决策码
 * @param summary 决策摘要
 * @param leaseOwner 租约持有者
 * @param leaseUntil 租约到期时间
 * @param recoveryAttemptId 已创建的恢复尝试 ID
 * @param attemptStatus 恢复尝试状态
 */
public record RecoveryPlanView(
        UUID taskRunId,
        UUID checkpointId,
        String checkpointType,
        InterruptionType interruptionType,
        RecoveryDisposition disposition,
        String decisionCode,
        String summary,
        String leaseOwner,
        Instant leaseUntil,
        UUID recoveryAttemptId,
        String attemptStatus) {
}
