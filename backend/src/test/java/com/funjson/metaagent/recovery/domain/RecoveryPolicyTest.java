package com.funjson.metaagent.recovery.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * 验证终态、活跃租约、损坏 Checkpoint 和未知副作用的恢复边界。
 */
class RecoveryPolicyTest {

    private static final Instant NOW =
            Instant.parse("2026-06-19T08:00:00Z");
    private final RecoveryPolicy policy = new RecoveryPolicy(
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void rejectsTerminalTaskRun() {
        assertThat(policy.decide(candidate(
                "COMPLETED",
                null,
                true,
                true,
                "NONE")).disposition())
                .isEqualTo(RecoveryDisposition.NOT_RECOVERABLE);
    }

    @Test
    void rejectsTaskRunWithActiveOwner() {
        assertThat(policy.decide(candidate(
                "RUNNING",
                NOW.plusSeconds(30),
                true,
                true,
                "NONE")).code())
                .isEqualTo("ACTIVE_LEASE");
    }

    @Test
    void requiresManualHandlingForInvalidCheckpoint() {
        assertThat(policy.decide(candidate(
                "RUNNING",
                NOW.minusSeconds(1),
                true,
                false,
                "NONE")).disposition())
                .isEqualTo(RecoveryDisposition.MANUAL_REQUIRED);
    }

    @Test
    void requiresReconciliationForUnknownSideEffect() {
        assertThat(policy.decide(candidate(
                "RUNNING",
                NOW.minusSeconds(1),
                true,
                true,
                "UNKNOWN")).disposition())
                .isEqualTo(RecoveryDisposition.RECONCILE_REQUIRED);
    }

    @Test
    void allowsSafeCheckpointAfterLeaseExpiry() {
        assertThat(policy.decide(candidate(
                "RUNNING",
                NOW.minusSeconds(1),
                true,
                true,
                "NONE")).disposition())
                .isEqualTo(RecoveryDisposition.AUTO_RESUME);
    }

    @Test
    void requiresManualHandlingForUnsupportedCursor() {
        RecoveryCandidate candidate = candidate(
                "RUNNING",
                NOW.minusSeconds(1),
                true,
                true,
                "NONE");
        candidate = new RecoveryCandidate(
                candidate.taskRunId(),
                candidate.jobId(),
                candidate.taskId(),
                candidate.taskRunStatus(),
                candidate.leaseOwner(),
                candidate.leaseUntil(),
                candidate.checkpointId(),
                "NODE_COMPLETE",
                candidate.checkpointRestorable(),
                candidate.checkpointChecksumValid(),
                candidate.loopRunId(),
                candidate.loopRunParentType(),
                candidate.loopNodeId(),
                candidate.loopNodeStatus(),
                candidate.actionType(),
                candidate.sideEffectClass());

        assertThat(policy.decide(candidate).code())
                .isEqualTo("CHECKPOINT_CURSOR_UNSUPPORTED");
    }

    @Test
    void allowsCompletedChildJobCursorReplay() {
        RecoveryCandidate candidate = candidate(
                "WAITING_CHILD_JOB",
                NOW.minusSeconds(1),
                true,
                true,
                "NONE");
        candidate = new RecoveryCandidate(
                candidate.taskRunId(),
                candidate.jobId(),
                candidate.taskId(),
                candidate.taskRunStatus(),
                candidate.leaseOwner(),
                candidate.leaseUntil(),
                candidate.checkpointId(),
                "CHILD_JOB_CREATED",
                candidate.checkpointRestorable(),
                candidate.checkpointChecksumValid(),
                candidate.loopRunId(),
                candidate.loopRunParentType(),
                candidate.loopNodeId(),
                candidate.loopNodeStatus(),
                candidate.actionType(),
                candidate.sideEffectClass());

        assertThat(policy.decide(candidate).disposition())
                .isEqualTo(RecoveryDisposition.AUTO_RESUME);
    }

    /**
     * 创建恢复候选。
     *
     * @param status TaskRun 状态
     * @param leaseUntil 租约时间
     * @param restorable 是否可恢复
     * @param checksumValid checksum 是否有效
     * @param sideEffectClass 副作用类型
     * @return 候选
     */
    private RecoveryCandidate candidate(
            String status,
            Instant leaseUntil,
            boolean restorable,
            boolean checksumValid,
            String sideEffectClass) {
        return new RecoveryCandidate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                leaseUntil == null ? null : "worker",
                leaseUntil,
                UUID.randomUUID(),
                "ACTION_PREPARED",
                restorable,
                checksumValid,
                UUID.randomUUID(),
                "TASK_RUN",
                UUID.randomUUID(),
                "RUNNING",
                "MODEL_CALL",
                sideEffectClass);
    }
}
