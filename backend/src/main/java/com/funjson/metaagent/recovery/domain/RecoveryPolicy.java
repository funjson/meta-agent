package com.funjson.metaagent.recovery.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;


/**
 * 根据租约、Checkpoint 和副作用分类决定能否自动恢复。
 */
public class RecoveryPolicy {

    private static final Set<String> TERMINAL_STATUSES =
            Set.of("COMPLETED", "FAILED", "CANCELLED");
    private static final Set<String> SUPPORTED_CHECKPOINT_TYPES =
            Set.of(
                    "RUN_START",
                    "CHILD_LOOP_CREATED",
                    "ACTION_PREPARED",
                    "CHILD_JOB_CREATED",
                    "CLARIFICATION_ANSWERED");
    private final Clock clock;

    /**
     * 创建使用系统 UTC 时钟的恢复策略。
     */
    public RecoveryPolicy() {
        this(Clock.systemUTC());
    }

    /**
     * 创建可测试的恢复策略。
     *
     * @param clock 时钟
     */
    RecoveryPolicy(Clock clock) {
        this.clock = clock;
    }

    /**
     * 评估恢复候选。
     *
     * @param candidate 候选
     * @return 恢复决策
     */
    public RecoveryDecision decide(RecoveryCandidate candidate) {
        if (TERMINAL_STATUSES.contains(candidate.taskRunStatus())) {
            return decision(
                    InterruptionType.TERMINAL_STATE,
                    RecoveryDisposition.NOT_RECOVERABLE,
                    "TERMINAL_TASK_RUN",
                    "TaskRun 已处于终态，不允许恢复");
        }
        if (candidate.leaseOwner() != null
                && candidate.leaseUntil() != null
                && candidate.leaseUntil().isAfter(Instant.now(clock))) {
            return decision(
                    InterruptionType.ACTIVE_OWNER,
                    RecoveryDisposition.NOT_RECOVERABLE,
                    "ACTIVE_LEASE",
                    "TaskRun 仍由有效 Worker 租约持有");
        }
        if (candidate.checkpointId() == null
                || !candidate.checkpointRestorable()
                || !candidate.checkpointChecksumValid()) {
            return decision(
                    InterruptionType.CHECKPOINT_INVALID,
                    RecoveryDisposition.MANUAL_REQUIRED,
                    "CHECKPOINT_UNAVAILABLE",
                    "没有完整且声明可恢复的 Checkpoint");
        }
        if (!SUPPORTED_CHECKPOINT_TYPES.contains(
                candidate.checkpointType())) {
            return decision(
                    InterruptionType.UNKNOWN,
                    RecoveryDisposition.MANUAL_REQUIRED,
                    "CHECKPOINT_CURSOR_UNSUPPORTED",
                    "当前恢复执行器尚不支持该 Checkpoint 游标");
        }
        if ("UNKNOWN".equals(candidate.sideEffectClass())
                || "IRREVERSIBLE".equals(candidate.sideEffectClass())) {
            return decision(
                    InterruptionType.UNKNOWN_SIDE_EFFECT,
                    RecoveryDisposition.RECONCILE_REQUIRED,
                    "SIDE_EFFECT_RECONCILIATION_REQUIRED",
                    "外部写入结果未知，恢复前必须先对账");
        }
        if ("PAUSED".equals(candidate.taskRunStatus())) {
            return decision(
                    InterruptionType.USER_PAUSE,
                    RecoveryDisposition.AUTO_RESUME,
                    "USER_PAUSE_SAFE_POINT",
                    "用户暂停且安全点有效，可以恢复");
        }
        return decision(
                InterruptionType.LEASE_EXPIRED,
                RecoveryDisposition.AUTO_RESUME,
                "SAFE_CHECKPOINT_AVAILABLE",
                "租约已失效且动作可重试，可从安全点恢复");
    }

    /** 创建决策。 */
    private RecoveryDecision decision(
            InterruptionType type,
            RecoveryDisposition disposition,
            String code,
            String summary) {
        return new RecoveryDecision(type, disposition, code, summary);
    }
}
