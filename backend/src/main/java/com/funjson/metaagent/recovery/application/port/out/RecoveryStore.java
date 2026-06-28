package com.funjson.metaagent.recovery.application.port.out;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.recovery.domain.LoopNodeResumeContext;
import com.funjson.metaagent.recovery.domain.RecoveryCandidate;
import com.funjson.metaagent.recovery.domain.RecoveryDecision;
import com.funjson.metaagent.recovery.domain.ResumeExecutionSnapshot;
import com.funjson.metaagent.runtime.domain.ChildJobOutcome;
import com.funjson.metaagent.runtime.domain.ClarificationAnswerOutcome;

/**
 * 定义租约、恢复游标和 RecoveryAttempt 的持久化端口。
 */
public interface RecoveryStore {

    /** @return 是否成功获取租约 */
    boolean acquireLease(
            UUID taskRunId,
            String workerId,
            Duration duration);

    /** @return 是否成功刷新心跳 */
    boolean heartbeat(
            UUID taskRunId,
            String workerId,
            Duration duration);

    /** 释放租约。 */
    void releaseLease(UUID taskRunId, String workerId);

    /** @return 恢复候选 */
    RecoveryCandidate requireCandidate(UUID taskRunId);

    /** @return ResumeExecutor 快照 */
    ResumeExecutionSnapshot requireResumeSnapshot(UUID taskRunId);

    /** @return 等待子执行的 origin LoopNode 恢复上下文 */
    LoopNodeResumeContext requireLoopNodeResumeContext(UUID loopNodeId);

    /** @return 已持久化的 ChildJobOutcome */
    ChildJobOutcome requireCompletedChildJobOutcome(UUID loopNodeId);

    /** @return 已绑定到 origin LoopNode 的澄清回答 */
    ClarificationAnswerOutcome requireAnsweredClarificationOutcome(
            UUID loopNodeId);

    /** @return 可由后台 Worker 尝试自动恢复的 TaskRun */
    List<UUID> findAutoRecoveryCandidates(int limit);

    /** 插入恢复尝试。 */
    void insertAttempt(
            UUID attemptId,
            RecoveryCandidate candidate,
            RecoveryDecision decision,
            String status,
            String contextJson);

    /** 更新恢复尝试。 */
    void updateAttempt(
            UUID attemptId,
            String status,
            String contextJson);
}
