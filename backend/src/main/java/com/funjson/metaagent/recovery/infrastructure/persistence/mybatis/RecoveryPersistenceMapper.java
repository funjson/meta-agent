package com.funjson.metaagent.recovery.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 定义租约、恢复候选和恢复审计的 MyBatis 映射。
 */
@Mapper
public interface RecoveryPersistenceMapper {

    /** @return 更新行数 */
    int acquireLease(
            @Param("taskRunId") UUID taskRunId,
            @Param("workerId") String workerId,
            @Param("leaseSeconds") long leaseSeconds);

    /** @return 更新行数 */
    int heartbeat(
            @Param("taskRunId") UUID taskRunId,
            @Param("workerId") String workerId,
            @Param("leaseSeconds") long leaseSeconds);

    /** @return 更新行数 */
    int releaseLease(
            @Param("taskRunId") UUID taskRunId,
            @Param("workerId") String workerId);

    /** @return 恢复候选 */
    Map<String, Object> findRecoveryCandidate(
            @Param("taskRunId") UUID taskRunId);

    /** @return ResumeExecutor 快照 */
    Map<String, Object> findResumeSnapshot(
            @Param("taskRunId") UUID taskRunId);

    /** @return LoopNode 恢复上下文 */
    Map<String, Object> findLoopNodeResumeContext(
            @Param("loopNodeId") UUID loopNodeId);

    /** @return ChildJobOutcome JSON */
    String findCompletedChildJobOutcome(
            @Param("loopNodeId") UUID loopNodeId);

    /** @return 已回答澄清请求行 */
    Map<String, Object> findAnsweredClarificationOutcome(
            @Param("loopNodeId") UUID loopNodeId);

    /** @return 可自动恢复的 TaskRun ID */
    List<String> findAutoRecoveryCandidates(@Param("limit") int limit);

    /** @return 插入行数 */
    int insertRecoveryAttempt(
            @Param("attemptId") UUID attemptId,
            @Param("taskRunId") UUID taskRunId,
            @Param("checkpointId") UUID checkpointId,
            @Param("interruptionType") String interruptionType,
            @Param("disposition") String disposition,
            @Param("decisionCode") String decisionCode,
            @Param("status") String status,
            @Param("contextJson") String contextJson);

    /** @return 更新行数 */
    int updateRecoveryAttempt(
            @Param("attemptId") UUID attemptId,
            @Param("status") String status,
            @Param("contextJson") String contextJson);
}
