package com.funjson.metaagent.loop.application.port.out;

import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.loop.domain.LoopNodeStatus;
import com.funjson.metaagent.loop.domain.LoopPhaseType;

/**
 * 定义 Task、Loop 和 Recovery 共用的运行时持久化端口。
 */
public interface RuntimeStore {

    /** 插入 LoopRun。 */
    void insertLoopRun(
            UUID loopRunId,
            UUID taskRunId,
            String parentType,
            UUID parentId,
            String policyJson,
            String scopedContextJson,
            int recursionDepth);

    /** 插入 LoopNode。 */
    void insertLoopNode(
            UUID loopNodeId,
            UUID loopRunId,
            UUID parentNodeId,
            int depth,
            int iterationNo,
            String idempotencyKey,
            UUID taskRunId,
            String providerId,
            String goal,
            String inputJson);

    /** @return LoopRun 的节点数 */
    int countLoopNodes(UUID loopRunId);

    /** @return LoopNode 当前状态 */
    LoopNodeStatus findLoopNodeStatus(UUID loopNodeId);

    /** 更新 LoopNode 结构化决策。 */
    void updateLoopNodeDecision(
            UUID loopNodeId,
            String actionType,
            String decisionJson);

    /** 插入完成阶段。 */
    void insertCompletedPhase(
            UUID phaseId,
            UUID loopNodeId,
            LoopPhaseType phaseType,
            String summary,
            String inputJson,
            String outputJson);

    /** 插入运行中阶段。 */
    void insertRunningPhase(
            UUID phaseId,
            UUID loopNodeId,
            LoopPhaseType phaseType,
            String summary,
            String inputJson);

    /** 完成阶段。 */
    void completePhase(
            UUID phaseId,
            String summary,
            String outputJson);

    /** 标记阶段失败。 */
    void failPhase(
            UUID phaseId,
            String summary,
            String outputJson);

    /** 复用可恢复 Action Phase。 */
    void reopenPhaseForRecovery(UUID phaseId);

    /** 标记 LoopNode 等待子执行。 */
    void markLoopNodeWaitingChildren(UUID loopNodeId);

    /** 标记 LoopNode 等待阻塞型 Child Job。 */
    void markLoopNodeWaitingChildJob(UUID loopNodeId);

    /** 标记 TaskRun 等待阻塞型 Child Job。 */
    void markTaskRunWaitingChildJob(UUID taskRunId);

    /** 标记 LoopNode 等待用户澄清回答。 */
    void markLoopNodeWaitingHuman(UUID loopNodeId);

    /** 标记 TaskRun 等待用户澄清回答。 */
    void markTaskRunWaitingHuman(UUID taskRunId);

    /** 恢复等待中的 LoopNode。 */
    void resumeLoopNode(UUID loopNodeId);

    /** 从 Child Job 等待状态恢复 TaskRun。 */
    void resumeTaskRunFromChildJob(UUID taskRunId);

    /** 从 WAITING_HUMAN 状态恢复 TaskRun。 */
    void resumeTaskRunFromHuman(UUID taskRunId);

    /** 设置 LoopRun 根节点。 */
    void setLoopRootNode(UUID loopRunId, UUID loopNodeId);

    /** 插入 Checkpoint。 */
    void insertCheckpoint(
            UUID checkpointId,
            UUID taskRunId,
            UUID loopRunId,
            UUID loopNodeId,
            long sequenceNo,
            String checkpointType,
            String stateJson,
            long eventOffset);

    /** @return 下一个 Checkpoint 序号 */
    long nextCheckpointSequence(UUID taskRunId);

    /** 更新最新 Checkpoint。 */
    void updateLatestCheckpoint(UUID taskRunId, UUID checkpointId);

    /** 完成 LoopNode。 */
    void completeLoopNode(
            UUID loopNodeId,
            String observationJson,
            String outputJson);

    /** 完成等待中的祖先 LoopNode。 */
    void completeWaitingLoopNodes(
            UUID loopRunId,
            String observationJson,
            String outputJson);

    /** 完成 LoopRun。 */
    void completeLoopRun(UUID loopRunId);

    /** 完成 TaskRun。 */
    void completeTaskRun(UUID taskRunId, String resultSummary);

    /** 标记 TaskRun 失败。 */
    void failTaskRun(UUID taskRunId, String failureSummary);

    /** 标记 Loop 执行失败。 */
    void failLoop(
            UUID loopRunId,
            UUID loopNodeId,
            String observationJson);

    /** 插入 Evidence。 */
    void insertEvidence(
            UUID evidenceId,
            UUID taskId,
            UUID taskRunId,
            UUID loopRunId,
            UUID loopNodeId,
            String evidenceType,
            String subjectRef,
            String detailsJson);

    /** 插入运行事件并返回实际序号。 */
    long insertRuntimeEvent(
            UUID eventId,
            UUID jobId,
            UUID taskId,
            UUID taskRunId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payloadJson);

    /** 插入 Outbox 事件。 */
    void insertOutboxEvent(
            UUID eventId,
            String eventType,
            String payloadJson);

}
