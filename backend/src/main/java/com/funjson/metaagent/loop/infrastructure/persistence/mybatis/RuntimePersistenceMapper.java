package com.funjson.metaagent.loop.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 定义 Loop Runtime Store 的 MyBatis 映射。
 */
@Mapper
public interface RuntimePersistenceMapper {

    /** @return 幂等命令关联的资源 ID */
    String findCommandResource(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("commandType") String commandType);

    /** @return 加锁后的 Job 行 */
    Map<String, Object> lockJob(@Param("jobId") UUID jobId);

    /** @return 加锁后的首个 Task 行 */
    Map<String, Object> lockReadyTask(@Param("jobId") UUID jobId);

    /** @return 加锁后的 READY Task 批次 */
    List<Map<String, Object>> lockReadyTasks(
            @Param("jobId") UUID jobId,
            @Param("limit") int limit);

    /** @return 下一个运行尝试序号 */
    int nextAttemptNo(@Param("taskId") UUID taskId);

    /** @return 更新行数 */
    int updateJobStatus(
            @Param("jobId") UUID jobId,
            @Param("status") String status);

    /** @return 更新行数 */
    int updateTaskStatus(
            @Param("taskId") UUID taskId,
            @Param("status") String status,
            @Param("activeTaskRunId") UUID activeTaskRunId);

    /** @return 插入行数 */
    int insertTaskRun(
            @Param("taskRunId") UUID taskRunId,
            @Param("taskId") UUID taskId,
            @Param("attemptNo") int attemptNo);

    /** @return 插入行数 */
    int insertTaskRunDispatch(
            @Param("dispatchId") UUID dispatchId,
            @Param("jobId") UUID jobId,
            @Param("taskId") UUID taskId,
            @Param("taskRunId") UUID taskRunId);

    /** @return 更新行数 */
    int claimTaskRunDispatch(
            @Param("dispatchId") UUID dispatchId,
            @Param("workerId") String workerId);

    /** @return 更新行数 */
    int finishTaskRunDispatch(
            @Param("dispatchId") UUID dispatchId,
            @Param("status") String status,
            @Param("lastError") String lastError);

    /** @return 插入行数 */
    int insertLoopRun(
            @Param("loopRunId") UUID loopRunId,
            @Param("taskRunId") UUID taskRunId,
            @Param("parentType") String parentType,
            @Param("parentId") UUID parentId,
            @Param("policyJson") String policyJson,
            @Param("scopedContextJson") String scopedContextJson,
            @Param("recursionDepth") int recursionDepth);

    /** @return 插入行数 */
    int insertLoopNode(
            @Param("loopNodeId") UUID loopNodeId,
            @Param("loopRunId") UUID loopRunId,
            @Param("parentNodeId") UUID parentNodeId,
            @Param("depth") int depth,
            @Param("iterationNo") int iterationNo,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("providerId") String providerId,
            @Param("goal") String goal,
            @Param("inputJson") String inputJson);

    /** @return 当前 LoopRun 节点数 */
    int countLoopNodes(@Param("loopRunId") UUID loopRunId);

    /**
     * 查询 LoopNode 当前状态。
     *
     * @param loopNodeId LoopNode ID
     * @return 状态名
     */
    String findLoopNodeStatus(@Param("loopNodeId") UUID loopNodeId);

    /** @return 更新行数 */
    int updateLoopNodeDecision(
            @Param("loopNodeId") UUID loopNodeId,
            @Param("actionType") String actionType,
            @Param("decisionJson") String decisionJson);

    /** @return 插入行数 */
    int insertCompletedPhase(
            @Param("phaseId") UUID phaseId,
            @Param("loopNodeId") UUID loopNodeId,
            @Param("phaseType") String phaseType,
            @Param("sequenceNo") int sequenceNo,
            @Param("summary") String summary,
            @Param("inputJson") String inputJson,
            @Param("outputJson") String outputJson);

    /** @return 插入行数 */
    int insertRunningPhase(
            @Param("phaseId") UUID phaseId,
            @Param("loopNodeId") UUID loopNodeId,
            @Param("phaseType") String phaseType,
            @Param("sequenceNo") int sequenceNo,
            @Param("summary") String summary,
            @Param("inputJson") String inputJson);

    /** @return 更新行数 */
    int completePhase(
            @Param("phaseId") UUID phaseId,
            @Param("summary") String summary,
            @Param("outputJson") String outputJson);

    /** @return 更新行数 */
    int failPhase(
            @Param("phaseId") UUID phaseId,
            @Param("summary") String summary,
            @Param("outputJson") String outputJson);

    /** @return 更新行数 */
    int reopenPhaseForRecovery(@Param("phaseId") UUID phaseId);

    /** @return 更新行数 */
    int updateLoopNodeCurrentPhase(
            @Param("loopNodeId") UUID loopNodeId,
            @Param("phaseType") String phaseType);

    /** @return 更新行数 */
    int markLoopNodeWaitingChildren(@Param("loopNodeId") UUID loopNodeId);

    /** @return 更新行数 */
    int markLoopNodeWaitingChildJob(@Param("loopNodeId") UUID loopNodeId);

    /** @return 更新行数 */
    int markTaskRunWaitingChildJob(@Param("taskRunId") UUID taskRunId);

    /** @return 更新行数 */
    int markLoopNodeWaitingHuman(@Param("loopNodeId") UUID loopNodeId);

    /** @return 更新行数 */
    int markTaskRunWaitingHuman(@Param("taskRunId") UUID taskRunId);

    /** @return 更新行数 */
    int resumeLoopNode(@Param("loopNodeId") UUID loopNodeId);

    /** @return 更新行数 */
    int resumeTaskRunFromChildJob(@Param("taskRunId") UUID taskRunId);

    /** @return 更新行数 */
    int resumeTaskRunFromHuman(@Param("taskRunId") UUID taskRunId);

    /** @return 更新行数 */
    int setLoopRootNode(
            @Param("loopRunId") UUID loopRunId,
            @Param("loopNodeId") UUID loopNodeId);

    /** @return 插入行数 */
    int insertCheckpoint(
            @Param("checkpointId") UUID checkpointId,
            @Param("taskRunId") UUID taskRunId,
            @Param("loopRunId") UUID loopRunId,
            @Param("loopNodeId") UUID loopNodeId,
            @Param("sequenceNo") long sequenceNo,
            @Param("checkpointType") String checkpointType,
            @Param("stateJson") String stateJson,
            @Param("eventOffset") long eventOffset);

    /** @return 下一个 Checkpoint 序号 */
    long nextCheckpointSequence(@Param("taskRunId") UUID taskRunId);

    /** @return 更新行数 */
    int updateLatestCheckpoint(
            @Param("taskRunId") UUID taskRunId,
            @Param("checkpointId") UUID checkpointId);

    /** @return 更新行数 */
    int completeLoopNode(
            @Param("loopNodeId") UUID loopNodeId,
            @Param("observationJson") String observationJson,
            @Param("outputJson") String outputJson);

    /** @return 更新行数 */
    int completeWaitingLoopNodes(
            @Param("loopRunId") UUID loopRunId,
            @Param("observationJson") String observationJson,
            @Param("outputJson") String outputJson);

    /** @return 更新行数 */
    int completeLoopRun(@Param("loopRunId") UUID loopRunId);

    /** @return 更新行数 */
    int completeTaskRun(
            @Param("taskRunId") UUID taskRunId,
            @Param("resultSummary") String resultSummary);

    /** @return 更新行数 */
    int failTaskRun(
            @Param("taskRunId") UUID taskRunId,
            @Param("failureSummary") String failureSummary);

    /** @return 更新行数 */
    int failLoopNode(
            @Param("loopNodeId") UUID loopNodeId,
            @Param("observationJson") String observationJson);

    /** @return 更新行数 */
    int failOpenLoopNodes(
            @Param("loopRunId") UUID loopRunId,
            @Param("observationJson") String observationJson);

    /** @return 更新行数 */
    int failLoopRun(@Param("loopRunId") UUID loopRunId);

    /** @return 插入行数 */
    int insertEvidence(
            @Param("evidenceId") UUID evidenceId,
            @Param("taskId") UUID taskId,
            @Param("taskRunId") UUID taskRunId,
            @Param("loopRunId") UUID loopRunId,
            @Param("loopNodeId") UUID loopNodeId,
            @Param("evidenceType") String evidenceType,
            @Param("subjectRef") String subjectRef,
            @Param("detailsJson") String detailsJson);

    /** @return 下一个事件序号 */
    long nextEventSequence(@Param("aggregateId") UUID aggregateId);

    /** @return 插入行数 */
    int insertRuntimeEvent(
            @Param("eventId") UUID eventId,
            @Param("jobId") UUID jobId,
            @Param("taskId") UUID taskId,
            @Param("taskRunId") UUID taskRunId,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") UUID aggregateId,
            @Param("eventType") String eventType,
            @Param("payloadJson") String payloadJson,
            @Param("sequenceNo") long sequenceNo);

    /** @return 插入行数 */
    int insertOutboxEvent(
            @Param("eventId") UUID eventId,
            @Param("eventType") String eventType,
            @Param("payloadJson") String payloadJson);

    /** @return 插入行数 */
    int registerCommand(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("commandType") String commandType,
            @Param("resourceId") UUID resourceId);

    /** @return TaskRun 基础行 */
    Map<String, Object> findTaskRun(@Param("taskRunId") UUID taskRunId);

    /** @return Checkpoint 行 */
    List<Map<String, Object>> findCheckpoints(
            @Param("taskRunId") UUID taskRunId);

    /** @return LoopNode 行 */
    List<Map<String, Object>> findLoopNodes(
            @Param("taskRunId") UUID taskRunId);

    /** @return LoopNode 阶段行 */
    List<Map<String, Object>> findLoopNodePhases(
            @Param("loopNodeId") UUID loopNodeId);

    /** @return Evidence 行 */
    List<Map<String, Object>> findEvidence(
            @Param("taskRunId") UUID taskRunId);
}
