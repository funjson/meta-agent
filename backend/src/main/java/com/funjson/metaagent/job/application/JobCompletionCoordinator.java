package com.funjson.metaagent.job.application;

import java.util.Map;
import java.util.UUID;

import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.job.domain.JobCompletionPolicy;
import com.funjson.metaagent.task.domain.TaskStatus;
import com.funjson.metaagent.task.domain.TaskCompletionPolicy;
import com.funjson.metaagent.job.application.port.out.JobCompletionStore;
import com.funjson.metaagent.loop.domain.LoopOutcome;
import com.funjson.metaagent.loop.application.port.out.RuntimeStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 由 Job 层根据 LoopOutcome 与 Evidence 推进 TaskGraph 和 Job 终态。
 */
@Service
public class  JobCompletionCoordinator {

    private final JobCompletionStore completionStore;
    private final RuntimeStore runtimeStore;
    private final ObjectMapper objectMapper;
    private final TaskCompletionPolicy taskCompletionPolicy;
    private final JobCompletionPolicy jobCompletionPolicy;

    /**
     * 创建 Job Completion Coordinator。
     *
     * @param completionStore 终态 Store Port
     * @param runtimeStore Runtime Store Port
     * @param objectMapper JSON 序列化器
     * @param taskCompletionPolicy Task 验收策略
     * @param jobCompletionPolicy Job 验收策略
     */
    public JobCompletionCoordinator(
            JobCompletionStore completionStore,
            RuntimeStore runtimeStore,
            ObjectMapper objectMapper,
            TaskCompletionPolicy taskCompletionPolicy,
            JobCompletionPolicy jobCompletionPolicy) {
        this.completionStore = completionStore;
        this.runtimeStore = runtimeStore;
        this.objectMapper = objectMapper;
        this.taskCompletionPolicy = taskCompletionPolicy;
        this.jobCompletionPolicy = jobCompletionPolicy;
    }

    /**
     * 验收成功的 LoopOutcome。
     *
     * @param outcome Loop 执行结果
     * @return Task Graph 推进结果
     */
    @Transactional
    public CompletionDecision accept(LoopOutcome outcome) {
        var taskDecision = taskCompletionPolicy.evaluate(outcome);
        if (!taskDecision.accepted()) {
            throw new IllegalArgumentException(
                    "Task completion rejected: "
                            + taskDecision.code());
        }

        var context = outcome.context();
        // 并行 Task 可以同时结束；Job 行锁保证依赖解锁和 Job 终态只收敛一次。
        completionStore.lockJob(context.jobId());
        // 只有 Job 层可以完成 Task，并在同一事务中推进依赖已满足的后继节点。
        completionStore.updateTaskStatus(context.taskId(), TaskStatus.COMPLETED);
        for (UUID taskId
                : completionStore.findUnblockedTaskIds(context.jobId())) {
            completionStore.updateTaskStatus(taskId, TaskStatus.READY);
        }
        long incompleteTaskCount =
                completionStore.countIncompleteTasks(context.jobId());
        long readyTaskCount =
                completionStore.countReadyTasks(context.jobId());
        var jobDecision = jobCompletionPolicy.evaluate(
                incompleteTaskCount,
                readyTaskCount);

        String payload = json(Map.of(
                "jobId", context.jobId(),
                "taskId", context.taskId(),
                "taskRunId", context.taskRunId(),
                "completionEvidenceId", outcome.evidenceId(),
                "acceptedBy", "JOB_COMPLETION_COORDINATOR",
                "remainingTaskCount", incompleteTaskCount,
                "readyTaskCount", readyTaskCount));
        runtimeStore.insertRuntimeEvent(
                UUID.randomUUID(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                "TASK",
                context.taskId(),
                "TASK_COMPLETED",
                payload);
        runtimeStore.insertOutboxEvent(
                UUID.randomUUID(),
                "TASK_COMPLETED",
                payload);

        if (jobDecision.completed()) {
            completionStore.updateJobStatus(
                    context.jobId(),
                    JobStatus.COMPLETED);
            runtimeStore.insertRuntimeEvent(
                    UUID.randomUUID(),
                    context.jobId(),
                    context.taskId(),
                    context.taskRunId(),
                    "JOB",
                    context.jobId(),
                    "JOB_COMPLETED",
                    payload);
            runtimeStore.insertOutboxEvent(
                    UUID.randomUUID(),
                    "JOB_COMPLETED",
                    payload);
        }
        return new CompletionDecision(
                jobDecision.completed(),
                readyTaskCount,
                incompleteTaskCount);
    }

    /**
     * 拒绝失败的 LoopOutcome 并记录 Job 层决策。
     *
     * @param outcome 失败结果
     */
    @Transactional
    public void reject(LoopOutcome outcome) {
        var context = outcome.context();
        completionStore.lockJob(context.jobId());
        completionStore.updateTaskStatus(context.taskId(), TaskStatus.FAILED);
        completionStore.updateJobStatus(context.jobId(), JobStatus.FAILED);

        String payload = json(Map.of(
                "jobId", context.jobId(),
                "taskId", context.taskId(),
                "taskRunId", context.taskRunId(),
                "failureSummary", outcome.failureSummary(),
                "decidedBy", "JOB_COMPLETION_COORDINATOR"));
        runtimeStore.insertRuntimeEvent(
                UUID.randomUUID(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                "JOB",
                context.jobId(),
                "JOB_FAILED",
                payload);
        runtimeStore.insertOutboxEvent(UUID.randomUUID(), "JOB_FAILED", payload);
    }

    /**
     * 序列化 Job 完成事件负载。
     *
     * @param value 负载
     * @return JSON
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize control decision", exception);
        }
    }

    /**
     * Job 层完成验收后的 Task Graph 调度摘要。
     *
     * @param jobCompleted Job 是否全部完成
     * @param readyTaskCount 可立即调度的 Task 数量
     * @param incompleteTaskCount 尚未完成的 Task 数量
     */
    public record CompletionDecision(
            boolean jobCompleted,
            long readyTaskCount,
            long incompleteTaskCount) {

        /**
         * 判断是否存在下一项可执行 Task。
         *
         * @return READY Task 是否非空
         */
        public boolean hasReadyTask() {
            return readyTaskCount > 0;
        }
    }
}
