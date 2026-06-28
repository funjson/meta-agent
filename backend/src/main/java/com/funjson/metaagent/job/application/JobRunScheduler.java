package com.funjson.metaagent.job.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.job.domain.JobRunStateGuard;
import com.funjson.metaagent.job.domain.LockedJobSnapshot;
import com.funjson.metaagent.task.domain.LockedTaskSnapshot;
import com.funjson.metaagent.task.domain.TaskStatus;
import com.funjson.metaagent.job.application.port.out.JobRunStore;
import com.funjson.metaagent.loop.domain.LoopNodeStatus;
import com.funjson.metaagent.loop.domain.LoopRunParentType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 由 Job 层调度 TaskGraph，并创建可恢复的 TaskRun/LoopRun 运行骨架。
 *
 * <p>Job 层负责选择并启动 Task；Loop Kernel 只在该骨架内执行闭环动作。</p>
 */
@Service
public class JobRunScheduler {

    private static final String START_COMMAND = "START_JOB";
    private static final int MAX_PARALLEL_TASKS = 4;

    private final JobRunStore runtimeStore;
    private final JobRunStateGuard stateGuard;
    private final ObjectMapper objectMapper;

    /**
     * 创建运行初始化服务。
     *
     * @param runtimeStore Job Run Store Port
     * @param stateGuard 状态机校验器
     * @param objectMapper JSON 序列化器
     */
    public JobRunScheduler(
            JobRunStore runtimeStore,
            JobRunStateGuard stateGuard,
            ObjectMapper objectMapper) {
        this.runtimeStore = runtimeStore;
        this.stateGuard = stateGuard;
        this.objectMapper = objectMapper;
    }

    /**
     * 锁定 Job/Task，并创建 TaskRun、LoopRun、根 LoopNode 和起始 Checkpoint。
     *
     * @param jobId Job ID
     * @param expectedVersion Job 期望版本
     * @param idempotencyKey 幂等键
     * @return 初始化结果
     */
    @Transactional
    public BeginResult begin(
            UUID jobId,
            long expectedVersion,
            String idempotencyKey) {
        DispatchBatch batch = beginWave(
                jobId,
                expectedVersion,
                idempotencyKey);
        return batch.isExisting()
                ? BeginResult.existing(batch.existingTaskRunId())
                : BeginResult.created(batch.dispatches().getFirst().context());
    }

    /**
     * 为已经处于 RUNNING 的 Job 调度下一个 READY Task。
     *
     * @param jobId Job ID
     * @param idempotencyKey 幂等键
     * @return 初始化结果
     */
    @Transactional
    public BeginResult beginNext(
            UUID jobId,
            String idempotencyKey) {
        DispatchBatch batch = beginNextWave(jobId, idempotencyKey);
        if (batch.dispatches().isEmpty()) {
            throw new com.funjson.metaagent.runtime.domain.RuntimeStateException(
                    "TASK_NOT_FOUND",
                    "Job has no executable task: " + jobId);
        }
        return BeginResult.created(batch.dispatches().getFirst().context());
    }

    /**
     * 原子物化初始并行 TaskRun 波次。
     *
     * @param jobId Job ID
     * @param expectedVersion Job 期望版本
     * @param idempotencyKey 幂等键
     * @return Dispatch 批次
     */
    @Transactional
    public DispatchBatch beginWave(
            UUID jobId,
            long expectedVersion,
            String idempotencyKey) {
        var existing = runtimeStore.findCommandResource(
                idempotencyKey,
                START_COMMAND);
        if (existing.isPresent()) {
            return DispatchBatch.existing(existing.get());
        }

        var job = runtimeStore.lockJob(jobId);
        stateGuard.requireJobStart(job, expectedVersion);
        List<LockedTaskSnapshot> tasks = runtimeStore.lockReadyTasks(
                jobId,
                MAX_PARALLEL_TASKS);
        if (tasks.isEmpty()) {
            throw new com.funjson.metaagent.runtime.domain.RuntimeStateException(
                    "TASK_NOT_FOUND",
                    "Job has no executable task: " + jobId);
        }
        runtimeStore.updateJobStatus(jobId, JobStatus.RUNNING);
        List<ScheduledTaskDispatch> dispatches = materialize(
                job,
                tasks,
                idempotencyKey,
                true);
        runtimeStore.registerCommand(
                idempotencyKey,
                START_COMMAND,
                dispatches.getFirst().context().taskRunId());
        return DispatchBatch.created(dispatches);
    }

    /**
     * 原子物化后续并行 TaskRun 波次。
     *
     * @param jobId Job ID
     * @param idempotencyKey 波次幂等前缀
     * @return Dispatch 批次；无 READY Task 时为空
     */
    @Transactional
    public DispatchBatch beginNextWave(
            UUID jobId,
            String idempotencyKey) {
        var job = runtimeStore.lockJob(jobId);
        stateGuard.requireJobContinuation(job);
        List<LockedTaskSnapshot> tasks = runtimeStore.lockReadyTasks(
                jobId,
                MAX_PARALLEL_TASKS);
        if (tasks.isEmpty()) {
            return DispatchBatch.created(List.of());
        }
        return DispatchBatch.created(materialize(
                job,
                tasks,
                idempotencyKey,
                false));
    }

    /**
     * 领取一个持久化 Dispatch。
     *
     * @param dispatchId Dispatch ID
     * @param workerId Worker ID
     */
    @Transactional
    public void claimDispatch(UUID dispatchId, String workerId) {
        if (!runtimeStore.claimTaskRunDispatch(dispatchId, workerId)) {
            throw new com.funjson.metaagent.runtime.domain.RuntimeStateException(
                    "TASK_DISPATCH_CLAIM_CONFLICT",
                    "TaskRun Dispatch is no longer pending: " + dispatchId);
        }
    }

    /**
     * 结束一个持久化 Dispatch。
     *
     * @param dispatchId Dispatch ID
     * @param success 是否成功执行到安全终点
     * @param failure 可选失败
     */
    @Transactional
    public void finishDispatch(
            UUID dispatchId,
            boolean success,
            RuntimeException failure) {
        runtimeStore.finishTaskRunDispatch(
                dispatchId,
                success ? "COMPLETED" : "FAILED",
                failure == null ? null : failure.getMessage());
    }

    /**
     * 为加锁 READY Task 创建独立 TaskRun、Loop 和 Dispatch。
     */
    private List<ScheduledTaskDispatch> materialize(
            LockedJobSnapshot job,
            List<LockedTaskSnapshot> tasks,
            String idempotencyPrefix,
            boolean initialWave) {
        List<ScheduledTaskDispatch> dispatches = new ArrayList<>();
        for (int index = 0; index < tasks.size(); index++) {
            LockedTaskSnapshot task = tasks.get(index);
            stateGuard.requireTaskReady(task);

            UUID taskRunId = UUID.randomUUID();
            UUID loopRunId = UUID.randomUUID();
            UUID loopNodeId = UUID.randomUUID();
            UUID checkpointId = UUID.randomUUID();
            UUID dispatchId = UUID.randomUUID();
            int attemptNo = runtimeStore.nextAttemptNo(task.id());

            runtimeStore.insertTaskRun(taskRunId, task.id(), attemptNo);
            runtimeStore.insertLoopRun(
                    loopRunId,
                    taskRunId,
                    "TASK_RUN",
                    taskRunId,
                    json(Map.of(
                            "maxDepth", 2,
                            "maxLoopNodes", 3)),
                    json(Map.of()),
                    0);
            runtimeStore.insertLoopNode(
                    loopNodeId,
                    loopRunId,
                    null,
                    0,
                    1,
                    "loop-node:" + loopRunId + ":root",
                    taskRunId,
                    job.providerId(),
                    task.goal(),
                    json(Map.of(
                            "taskGoal", task.goal(),
                            "provider", job.providerId(),
                            "dependencyContext",
                            task.dependencyContext() == null
                                    ? ""
                                    : task.dependencyContext())));
            runtimeStore.setLoopRootNode(loopRunId, loopNodeId);
            runtimeStore.updateTaskStatus(
                    task.id(),
                    TaskStatus.RUNNING,
                    taskRunId);
            runtimeStore.insertTaskRunDispatch(
                    dispatchId,
                    job.id(),
                    task.id(),
                    taskRunId);

            String eventType = initialWave && index == 0
                    ? "JOB_STARTED"
                    : "TASK_STARTED";
            String eventPayload = json(Map.of(
                    "jobId", job.id(),
                    "taskId", task.id(),
                    "taskRunId", taskRunId,
                    "loopRunId", loopRunId,
                    "loopNodeId", loopNodeId,
                    "dispatchId", dispatchId,
                    "provider", job.providerId()));
            long eventOffset = runtimeStore.insertRuntimeEvent(
                    UUID.randomUUID(),
                    job.id(),
                    task.id(),
                    taskRunId,
                    "JOB",
                    job.id(),
                    eventType,
                    eventPayload);
            runtimeStore.insertOutboxEvent(
                    UUID.randomUUID(),
                    eventType,
                    eventPayload);

            String checkpointState = json(Map.of(
                    "jobId", job.id(),
                    "taskId", task.id(),
                    "taskRunId", taskRunId,
                    "loopRunId", loopRunId,
                    "loopNodeId", loopNodeId,
                    "dispatchId", dispatchId,
                    "nodeStatus", LoopNodeStatus.RUNNING.name(),
                    "pendingAction", "MODEL_CALL",
                    "provider", job.providerId(),
                    "idempotencyPrefix", idempotencyPrefix));
            // 每个并行 TaskRun 在外部调用前都有自己的恢复安全点。
            runtimeStore.insertCheckpoint(
                    checkpointId,
                    taskRunId,
                    loopRunId,
                    loopNodeId,
                    1,
                    "RUN_START",
                    checkpointState,
                    eventOffset);
            runtimeStore.updateLatestCheckpoint(taskRunId, checkpointId);

            dispatches.add(new ScheduledTaskDispatch(
                    dispatchId,
                    new RunExecutionContext(
                            job.id(),
                            task.id(),
                            taskRunId,
                            loopRunId,
                            loopNodeId,
                            null,
                            0,
                            1,
                            LoopRunParentType.TASK_RUN,
                            taskRunId,
                            0,
                            job.providerId(),
                            task.goal(),
                            task.dependencyContext() == null
                                    ? ""
                                    : task.dependencyContext(),
                            job.rootCapability())));
        }
        return List.copyOf(dispatches);
    }

    /**
     * 序列化持久化状态。
     *
     * @param value 状态值
     * @return JSON
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize control initialization state",
                    exception);
        }
    }

    /**
     * 运行初始化结果。
     *
     * @param context 新运行上下文
     * @param existingTaskRunId 幂等重试命中的 TaskRun
     */
    public record BeginResult(
            RunExecutionContext context,
            UUID existingTaskRunId) {

        /**
         * 创建新运行结果。
         *
         * @param context 执行上下文
         * @return 初始化结果
         */
        public static BeginResult created(RunExecutionContext context) {
            return new BeginResult(context, null);
        }

        /**
         * 创建幂等命中结果。
         *
         * @param taskRunId 已存在 TaskRun
         * @return 初始化结果
         */
        public static BeginResult existing(UUID taskRunId) {
            return new BeginResult(null, taskRunId);
        }

        /**
         * 判断是否命中已有运行。
         *
         * @return 是否已有 TaskRun
         */
        public boolean isExisting() {
            return existingTaskRunId != null;
        }
    }

    /**
     * 一个可被 Worker 独立领取的 TaskRun Dispatch。
     *
     * @param dispatchId Dispatch ID
     * @param context Loop 执行上下文
     */
    public record ScheduledTaskDispatch(
            UUID dispatchId,
            RunExecutionContext context) {
    }

    /**
     * 同一 TaskGraph 层级的并行 Dispatch 批次。
     *
     * @param dispatches 新创建 Dispatch
     * @param existingTaskRunId 幂等命中的 TaskRun
     */
    public record DispatchBatch(
            List<ScheduledTaskDispatch> dispatches,
            UUID existingTaskRunId) {

        /** 复制 Dispatch 集合。 */
        public DispatchBatch {
            dispatches = List.copyOf(dispatches);
        }

        /** 创建新批次。 */
        public static DispatchBatch created(
                List<ScheduledTaskDispatch> dispatches) {
            return new DispatchBatch(dispatches, null);
        }

        /** 创建幂等命中批次。 */
        public static DispatchBatch existing(UUID taskRunId) {
            return new DispatchBatch(List.of(), taskRunId);
        }

        /** @return 是否命中已有运行 */
        public boolean isExisting() {
            return existingTaskRunId != null;
        }
    }
}
