package com.funjson.metaagent.loop.infrastructure.persistence.mybatis;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.task.domain.TaskStatus;
import com.funjson.metaagent.job.domain.LockedJobSnapshot;
import com.funjson.metaagent.task.domain.LockedTaskSnapshot;
import com.funjson.metaagent.job.application.port.out.JobRunStore;
import com.funjson.metaagent.loop.api.CheckpointView;
import com.funjson.metaagent.loop.api.EvidenceView;
import com.funjson.metaagent.loop.api.LoopNodeView;
import com.funjson.metaagent.loop.api.LoopPhaseView;
import com.funjson.metaagent.task.api.TaskRunView;
import com.funjson.metaagent.task.application.port.out.TaskRunQueryStore;
import com.funjson.metaagent.loop.domain.LoopNodeStatus;
import com.funjson.metaagent.loop.domain.LoopPhaseStatus;
import com.funjson.metaagent.loop.domain.LoopPhaseType;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.task.domain.TaskRunStatus;
import com.funjson.metaagent.loop.application.port.out.RuntimeStore;
import org.springframework.stereotype.Repository;

/**
 * 适配 Loop Application 与 Runtime MyBatis Mapper。
 *
 * <p>该类保留运行时的领域友好接口；所有 SQL、行锁和数据库方言均下沉至 Mapper
 * XML，避免 Application 层感知持久化细节。</p>
 */
@Repository
public class RuntimeRepository
        implements RuntimeStore, JobRunStore, TaskRunQueryStore {

    private final RuntimePersistenceMapper mapper;

    /**
     * 创建 Runtime Repository。
     *
     * @param mapper Runtime MyBatis Mapper
     */
    public RuntimeRepository(RuntimePersistenceMapper mapper) {
        this.mapper = mapper;
    }

    /** @return 幂等命令关联资源 */
    public Optional<UUID> findCommandResource(
            String idempotencyKey,
            String commandType) {
        return Optional.ofNullable(
                        mapper.findCommandResource(idempotencyKey, commandType))
                .map(UUID::fromString);
    }

    /** @return 加锁 Job */
    public LockedJobSnapshot lockJob(UUID jobId) {
        Map<String, Object> row = mapper.lockJob(jobId);
        if (row == null) {
            throw new RuntimeStateException(
                    "JOB_NOT_FOUND",
                    "Job not found: " + jobId);
        }
        return new LockedJobSnapshot(
                UUID.fromString(text(row, "id")),
                JobStatus.valueOf(text(row, "status")),
                number(row, "version").longValue(),
                text(row, "providerId"),
                nullableCapabilityRef(row));
    }

    /** @return 加锁 Task */
    public LockedTaskSnapshot lockReadyTask(UUID jobId) {
        Map<String, Object> row = mapper.lockReadyTask(jobId);
        if (row == null) {
            throw new RuntimeStateException(
                    "TASK_NOT_FOUND",
                    "Job has no executable task: " + jobId);
        }
        return toLockedTask(row);
    }

    /** 转换加锁 Task 数据库行。 */
    private LockedTaskSnapshot toLockedTask(Map<String, Object> row) {
        return new LockedTaskSnapshot(
                UUID.fromString(text(row, "id")),
                text(row, "goal"),
                nullableText(row.get("dependencyContext")),
                TaskStatus.valueOf(text(row, "status")),
                number(row, "version").longValue());
    }

    /** @return 加锁 READY Task 批次 */
    public List<LockedTaskSnapshot> lockReadyTasks(
            UUID jobId,
            int limit) {
        return mapper.lockReadyTasks(jobId, limit).stream()
                .map(this::toLockedTask)
                .toList();
    }

    /** @return 下一个尝试序号 */
    public int nextAttemptNo(UUID taskId) {
        return mapper.nextAttemptNo(taskId);
    }

    /** 更新 Job 状态。 */
    public void updateJobStatus(UUID jobId, JobStatus status) {
        mapper.updateJobStatus(jobId, status.name());
    }

    /** 更新 Task 状态和活跃运行。 */
    public void updateTaskStatus(
            UUID taskId,
            TaskStatus status,
            UUID activeTaskRunId) {
        mapper.updateTaskStatus(taskId, status.name(), activeTaskRunId);
    }

    /** 插入 TaskRun。 */
    public void insertTaskRun(UUID taskRunId, UUID taskId, int attemptNo) {
        mapper.insertTaskRun(taskRunId, taskId, attemptNo);
    }

    /** 插入持久化 TaskRun Dispatch。 */
    public void insertTaskRunDispatch(
            UUID dispatchId,
            UUID jobId,
            UUID taskId,
            UUID taskRunId) {
        mapper.insertTaskRunDispatch(
                dispatchId,
                jobId,
                taskId,
                taskRunId);
    }

    /** @return 是否成功领取 Dispatch */
    public boolean claimTaskRunDispatch(
            UUID dispatchId,
            String workerId) {
        return mapper.claimTaskRunDispatch(dispatchId, workerId) == 1;
    }

    /** 完成或失败 Dispatch。 */
    public void finishTaskRunDispatch(
            UUID dispatchId,
            String status,
            String lastError) {
        mapper.finishTaskRunDispatch(dispatchId, status, lastError);
    }

    /** 插入 TaskRun 拥有的 LoopRun。 */
    public void insertLoopRun(
            UUID loopRunId,
            UUID taskRunId,
            String parentType,
            UUID parentId,
            String policyJson,
            String scopedContextJson,
            int recursionDepth) {
        mapper.insertLoopRun(
                loopRunId,
                taskRunId,
                parentType,
                parentId,
                policyJson,
                scopedContextJson,
                recursionDepth);
    }

    /** 插入一个执行完整内部阶段闭环的 LoopNode。 */
    public void insertLoopNode(
            UUID loopNodeId,
            UUID loopRunId,
            UUID parentNodeId,
            int depth,
            int iterationNo,
            String idempotencyKey,
            UUID taskRunId,
            String providerId,
            String goal,
            String inputJson) {
        mapper.insertLoopNode(
                loopNodeId,
                loopRunId,
                parentNodeId,
                depth,
                iterationNo,
                idempotencyKey,
                providerId,
                goal,
                inputJson);
    }

    /** @return 当前 LoopRun 的节点数 */
    public int countLoopNodes(UUID loopRunId) {
        return mapper.countLoopNodes(loopRunId);
    }

    /**
     * 查询 LoopNode 当前状态。
     *
     * @param loopNodeId LoopNode ID
     * @return 当前状态
     */
    public LoopNodeStatus findLoopNodeStatus(UUID loopNodeId) {
        String status = mapper.findLoopNodeStatus(loopNodeId);
        if (status == null) {
            throw new RuntimeStateException(
                    "LOOP_NODE_NOT_FOUND",
                    "Loop node not found: " + loopNodeId);
        }
        return LoopNodeStatus.valueOf(status);
    }

    /** 更新 Planning 阶段形成的结构化节点决策。 */
    public void updateLoopNodeDecision(
            UUID loopNodeId,
            String actionType,
            String decisionJson) {
        mapper.updateLoopNodeDecision(
                loopNodeId,
                actionType,
                decisionJson);
    }

    /** 插入一个已完成的内部阶段并推进节点当前阶段。 */
    public void insertCompletedPhase(
            UUID phaseId,
            UUID loopNodeId,
            LoopPhaseType phaseType,
            String summary,
            String inputJson,
            String outputJson) {
        mapper.insertCompletedPhase(
                phaseId,
                loopNodeId,
                phaseType.name(),
                phaseType.sequence(),
                summary,
                inputJson,
                outputJson);
        mapper.updateLoopNodeCurrentPhase(loopNodeId, phaseType.name());
    }

    /** 插入一个跨外部调用边界的运行中阶段。 */
    public void insertRunningPhase(
            UUID phaseId,
            UUID loopNodeId,
            LoopPhaseType phaseType,
            String summary,
            String inputJson) {
        mapper.insertRunningPhase(
                phaseId,
                loopNodeId,
                phaseType.name(),
                phaseType.sequence(),
                summary,
                inputJson);
        mapper.updateLoopNodeCurrentPhase(loopNodeId, phaseType.name());
    }

    /** 完成运行中的阶段。 */
    public void completePhase(
            UUID phaseId,
            String summary,
            String outputJson) {
        mapper.completePhase(phaseId, summary, outputJson);
    }

    /** 标记运行中的阶段失败。 */
    public void failPhase(
            UUID phaseId,
            String summary,
            String outputJson) {
        mapper.failPhase(phaseId, summary, outputJson);
    }

    /**
     * 对无副作用动作复用既有 ACTION_EXECUTION Phase。
     *
     * @param phaseId Phase ID
     */
    public void reopenPhaseForRecovery(UUID phaseId) {
        if (mapper.reopenPhaseForRecovery(phaseId) != 1) {
            throw new RuntimeStateException(
                    "ACTION_PHASE_NOT_RECOVERABLE",
                    "Action phase cannot be reopened: " + phaseId);
        }
    }

    /** 把父节点置为等待 Child LoopNode。 */
    public void markLoopNodeWaitingChildren(UUID loopNodeId) {
        mapper.markLoopNodeWaitingChildren(loopNodeId);
    }

    /** 标记 LoopNode 等待阻塞型 Child Job。 */
    public void markLoopNodeWaitingChildJob(UUID loopNodeId) {
        if (mapper.markLoopNodeWaitingChildJob(loopNodeId) != 1) {
            throw new RuntimeStateException(
                    "INVALID_STATE_TRANSITION",
                    "Loop node cannot wait for child job: " + loopNodeId);
        }
    }

    /** 标记 TaskRun 等待阻塞型 Child Job。 */
    public void markTaskRunWaitingChildJob(UUID taskRunId) {
        if (mapper.markTaskRunWaitingChildJob(taskRunId) != 1) {
            throw new RuntimeStateException(
                    "INVALID_STATE_TRANSITION",
                    "Task run cannot wait for child job: " + taskRunId);
        }
    }

    /** 标记 LoopNode 等待用户澄清回答。 */
    public void markLoopNodeWaitingHuman(UUID loopNodeId) {
        if (mapper.markLoopNodeWaitingHuman(loopNodeId) != 1) {
            throw new RuntimeStateException(
                    "INVALID_STATE_TRANSITION",
                    "Loop node cannot wait for human: " + loopNodeId);
        }
    }

    /** 标记 TaskRun 等待用户澄清回答。 */
    public void markTaskRunWaitingHuman(UUID taskRunId) {
        if (mapper.markTaskRunWaitingHuman(taskRunId) != 1) {
            throw new RuntimeStateException(
                    "INVALID_STATE_TRANSITION",
                    "Task run cannot wait for human: " + taskRunId);
        }
    }

    /**
     * 子执行完成后恢复父 LoopNode。
     *
     * @param loopNodeId LoopNode ID
     */
    public void resumeLoopNode(UUID loopNodeId) {
        if (mapper.resumeLoopNode(loopNodeId) != 1) {
            throw new RuntimeStateException(
                    "LOOP_NODE_NOT_WAITING",
                    "Loop node is not waiting for children: " + loopNodeId);
        }
    }

    /** 从 Child Job 等待状态恢复 TaskRun。 */
    public void resumeTaskRunFromChildJob(UUID taskRunId) {
        if (mapper.resumeTaskRunFromChildJob(taskRunId) != 1) {
            throw new RuntimeStateException(
                    "INVALID_STATE_TRANSITION",
                    "Task run cannot resume from child job: " + taskRunId);
        }
    }

    /** 从 WAITING_HUMAN 状态恢复 TaskRun。 */
    public void resumeTaskRunFromHuman(UUID taskRunId) {
        if (mapper.resumeTaskRunFromHuman(taskRunId) != 1) {
            throw new RuntimeStateException(
                    "INVALID_STATE_TRANSITION",
                    "Task run cannot resume from human answer: " + taskRunId);
        }
    }

    /** 设置 LoopRun 根节点。 */
    public void setLoopRootNode(UUID loopRunId, UUID loopNodeId) {
        mapper.setLoopRootNode(loopRunId, loopNodeId);
    }

    /** 插入 Checkpoint。 */
    public void insertCheckpoint(
            UUID checkpointId,
            UUID taskRunId,
            UUID loopRunId,
            UUID loopNodeId,
            long sequenceNo,
            String checkpointType,
            String stateJson,
            long eventOffset) {
        mapper.insertCheckpoint(
                checkpointId,
                taskRunId,
                loopRunId,
                loopNodeId,
                sequenceNo,
                checkpointType,
                stateJson,
                eventOffset);
    }

    /** @return TaskRun 的下一个 Checkpoint 序号 */
    public long nextCheckpointSequence(UUID taskRunId) {
        return mapper.nextCheckpointSequence(taskRunId);
    }

    /** 更新 TaskRun 最近 Checkpoint。 */
    public void updateLatestCheckpoint(UUID taskRunId, UUID checkpointId) {
        mapper.updateLatestCheckpoint(taskRunId, checkpointId);
    }

    /** 完成 LoopNode。 */
    public void completeLoopNode(
            UUID loopNodeId,
            String observationJson,
            String outputJson) {
        mapper.completeLoopNode(loopNodeId, observationJson, outputJson);
    }

    /** 完成当前派生链中等待 Child 的祖先节点。 */
    public void completeWaitingLoopNodes(
            UUID loopRunId,
            String observationJson,
            String outputJson) {
        mapper.completeWaitingLoopNodes(
                loopRunId,
                observationJson,
                outputJson);
    }

    /** 完成 LoopRun。 */
    public void completeLoopRun(UUID loopRunId) {
        mapper.completeLoopRun(loopRunId);
    }

    /** 完成 TaskRun，但不修改 Task/Job 终态。 */
    public void completeTaskRun(UUID taskRunId, String resultSummary) {
        mapper.completeTaskRun(taskRunId, resultSummary);
    }

    /** 标记 TaskRun 失败。 */
    public void failTaskRun(UUID taskRunId, String failureSummary) {
        mapper.failTaskRun(taskRunId, failureSummary);
    }

    /** 标记 LoopNode 和 LoopRun 失败。 */
    public void failLoop(
            UUID loopRunId,
            UUID loopNodeId,
            String observationJson) {
        mapper.failLoopNode(loopNodeId, observationJson);
        mapper.failOpenLoopNodes(loopRunId, observationJson);
        mapper.failLoopRun(loopRunId);
    }

    /** 插入完成 Evidence。 */
    public void insertEvidence(
            UUID evidenceId,
            UUID taskId,
            UUID taskRunId,
            UUID loopRunId,
            UUID loopNodeId,
            String evidenceType,
            String subjectRef,
            String detailsJson) {
        mapper.insertEvidence(
                evidenceId,
                taskId,
                taskRunId,
                loopRunId,
                loopNodeId,
                evidenceType,
                subjectRef,
                detailsJson);
    }

    /** @return 聚合的下一个事件序号 */
    public long nextEventSequence(UUID aggregateId) {
        return mapper.nextEventSequence(aggregateId);
    }

    /**
     * 插入运行事件并返回实际序号。
     *
     * @return 事件序号
     */
    public long insertRuntimeEvent(
            UUID eventId,
            UUID jobId,
            UUID taskId,
            UUID taskRunId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payloadJson) {
        long sequence = nextEventSequence(aggregateId);
        mapper.insertRuntimeEvent(
                eventId,
                jobId,
                taskId,
                taskRunId,
                aggregateType,
                aggregateId,
                eventType,
                payloadJson,
                sequence);
        return sequence;
    }

    /** 插入 Outbox 事件。 */
    public void insertOutboxEvent(
            UUID eventId,
            String eventType,
            String payloadJson) {
        mapper.insertOutboxEvent(eventId, eventType, payloadJson);
    }

    /** 注册命令幂等键。 */
    public void registerCommand(
            String idempotencyKey,
            String commandType,
            UUID resourceId) {
        mapper.registerCommand(idempotencyKey, commandType, resourceId);
    }

    /** @return 完整 TaskRun 视图 */
    public TaskRunView findTaskRun(UUID taskRunId) {
        Map<String, Object> row = mapper.findTaskRun(taskRunId);
        if (row == null) {
            throw new RuntimeStateException(
                    "TASK_RUN_NOT_FOUND",
                    "TaskRun not found: " + taskRunId);
        }
        return new TaskRunView(
                UUID.fromString(text(row, "id")),
                UUID.fromString(text(row, "taskId")),
                text(row, "runType"),
                TaskRunStatus.valueOf(text(row, "status")),
                number(row, "attemptNo").intValue(),
                nullableText(row.get("resultSummary")),
                instant(row.get("startedAt")),
                instant(row.get("completedAt")),
                findLoopNodes(taskRunId),
                findCheckpoints(taskRunId),
                findEvidence(taskRunId));
    }

    /** @return Checkpoint 视图列表 */
    public List<CheckpointView> findCheckpoints(UUID taskRunId) {
        return mapper.findCheckpoints(taskRunId).stream()
                .map(row -> new CheckpointView(
                        UUID.fromString(text(row, "id")),
                        number(row, "sequenceNo").longValue(),
                        text(row, "checkpointType"),
                        booleanValue(row.get("restorable")),
                        text(row, "checksum"),
                        booleanValue(row.get("checksumValid")),
                        instant(row.get("createdAt"))))
                .toList();
    }

    /** @return LoopNode 视图列表 */
    private List<LoopNodeView> findLoopNodes(UUID taskRunId) {
        return mapper.findLoopNodes(taskRunId).stream()
                .map(row -> new LoopNodeView(
                        UUID.fromString(text(row, "id")),
                        uuid(row.get("parentNodeId")),
                        number(row, "depth").intValue(),
                        number(row, "iterationNo").intValue(),
                        text(row, "actionType"),
                        text(row, "goal"),
                        LoopNodeStatus.valueOf(text(row, "status")),
                        nullableText(row.get("currentPhase")),
                        nullableText(row.get("observation")),
                        nullableText(row.get("output")),
                        findLoopNodePhases(
                                UUID.fromString(text(row, "id"))),
                        instant(row.get("startedAt")),
                        instant(row.get("completedAt"))))
                .toList();
    }

    /** @return LoopNode 内部阶段列表 */
    private List<LoopPhaseView> findLoopNodePhases(UUID loopNodeId) {
        return mapper.findLoopNodePhases(loopNodeId).stream()
                .map(row -> new LoopPhaseView(
                        UUID.fromString(text(row, "id")),
                        LoopPhaseType.valueOf(text(row, "phaseType")),
                        number(row, "sequenceNo").intValue(),
                        LoopPhaseStatus.valueOf(text(row, "status")),
                        text(row, "summary"),
                        instant(row.get("startedAt")),
                        instant(row.get("completedAt"))))
                .toList();
    }

    /** @return Evidence 视图列表 */
    private List<EvidenceView> findEvidence(UUID taskRunId) {
        return mapper.findEvidence(taskRunId).stream()
                .map(row -> new EvidenceView(
                        UUID.fromString(text(row, "id")),
                        text(row, "evidenceType"),
                        text(row, "subjectRef"),
                        text(row, "result"),
                        instant(row.get("createdAt"))))
                .toList();
    }

    /** 读取必填字符串。 */
    private String text(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }

    /** 读取可空字符串。 */
    private String nullableText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 读取数值。 */
    private Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    /** 读取 MySQL 布尔值。 */
    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return ((Number) value).intValue() != 0;
    }

    /** 转换数据库时间。 */
    private Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.atZone(java.time.ZoneOffset.UTC).toInstant();
        }
        throw new IllegalArgumentException("Unsupported timestamp value: " + value);
    }

    /** 转换可空 UUID。 */
    private UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(String.valueOf(value));
    }

    /** 读取 AgentProfile 配置的根 Capability。 */
    private CapabilityRef nullableCapabilityRef(Map<String, Object> row) {
        Object id = row.get("rootCapabilityId");
        Object version = row.get("rootCapabilityVersion");
        if (id == null || version == null) {
            return null;
        }
        return new CapabilityRef(
                String.valueOf(id),
                ((Number) version).intValue());
    }

}
