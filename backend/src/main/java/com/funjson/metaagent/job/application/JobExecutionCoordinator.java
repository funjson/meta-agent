package com.funjson.metaagent.job.application;

import com.funjson.metaagent.loop.application.LoopRunExecutor;
import com.funjson.metaagent.loop.application.RuntimeExecutionService;
import com.funjson.metaagent.loop.application.RuntimeTransactionService;
import com.funjson.metaagent.loop.domain.LoopExecutionException;
import com.funjson.metaagent.loop.domain.LoopOutcome;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.recovery.application.RuntimeLeaseService;
import com.funjson.metaagent.recovery.application.port.out.RecoveryStore;
import com.funjson.metaagent.task.api.TaskRunView;
import com.funjson.metaagent.task.application.TaskRunQueryService;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 协调 Job 下 TaskGraph 的同步执行基线。
 *
 * <p>该协调器拥有 Job/Task 推进权，Loop Kernel 只执行已经创建好的
 * {@link RunExecutionContext} 并返回 {@link LoopOutcome}。</p>
 */
@Service
public class JobExecutionCoordinator {

    private final JobRunScheduler runScheduler;
    private final LoopRunExecutor loopRunExecutor;
    private final RuntimeTransactionService transactions;
    private final TaskRunQueryService taskRunQueries;
    private final JobCompletionCoordinator completionCoordinator;
    private final ChildJobCoordinator childJobCoordinator;
    private final ChildJobCompletionCoordinator childJobCompletionCoordinator;
    private final RuntimeLeaseService runtimeLeases;
    private final RecoveryStore recoveryStore;
    private final RuntimeExecutionService runtimeExecutionService;
    private final ExecutorService taskExecutor =
            Executors.newVirtualThreadPerTaskExecutor();
    private final String workerId = "local-worker-" + UUID.randomUUID();

    /**
     * 创建 Job 执行协调器。
     *
     * @param runScheduler Job/TaskRun 运行骨架调度器
     * @param loopRunExecutor Loop Kernel 执行端口
     * @param transactions Loop 事务服务
     * @param completionCoordinator 上层完成与 TaskGraph 推进协调器
     * @param runtimeLeases TaskRun 租约服务
     * @param taskRunQueries TaskRun 查询服务
     * @param childJobCoordinator Child Job 物化协调器
     * @param childJobCompletionCoordinator Child Job 完成协调器
     * @param recoveryStore 父 LoopNode 恢复上下文 Store
     * @param runtimeExecutionService Loop Kernel 恢复入口
     */
    public JobExecutionCoordinator(
            JobRunScheduler runScheduler,
            LoopRunExecutor loopRunExecutor,
            RuntimeTransactionService transactions,
            JobCompletionCoordinator completionCoordinator,
            RuntimeLeaseService runtimeLeases,
            TaskRunQueryService taskRunQueries,
            ChildJobCoordinator childJobCoordinator,
            ChildJobCompletionCoordinator childJobCompletionCoordinator,
            RecoveryStore recoveryStore,
            RuntimeExecutionService runtimeExecutionService) {
        this.runScheduler = runScheduler;
        this.loopRunExecutor = loopRunExecutor;
        this.transactions = transactions;
        this.completionCoordinator = completionCoordinator;
        this.runtimeLeases = runtimeLeases;
        this.taskRunQueries = taskRunQueries;
        this.childJobCoordinator = childJobCoordinator;
        this.childJobCompletionCoordinator =
                childJobCompletionCoordinator;
        this.recoveryStore = recoveryStore;
        this.runtimeExecutionService = runtimeExecutionService;
    }

    /**
     * 启动 Job 并依次执行当前同步基线中的 READY Task。
     *
     * @param jobId Job ID
     * @param expectedVersion Job 期望版本
     * @param idempotencyKey 幂等键
     * @return 最后完成的 TaskRun
     */
    public TaskRunView startJob(
            UUID jobId,
            long expectedVersion,
            String idempotencyKey) {
        JobRunScheduler.DispatchBatch batch =
                runScheduler.beginWave(
                        jobId,
                        expectedVersion,
                        idempotencyKey);
        if (batch.isExisting()) {
            return taskRunQueries.get(batch.existingTaskRunId());
        }

        TaskRunView lastRun = null;
        int wave = 1;
        while (!batch.dispatches().isEmpty()) {
            List<ScheduledTaskResult> results = executeWave(
                    batch.dispatches());
            lastRun = results.getLast().taskRun();
            if (results.stream().anyMatch(
                    ScheduledTaskResult::jobCompleted)) {
                break;
            }
            wave++;
            batch = runScheduler.beginNextWave(
                    jobId,
                    idempotencyKey + ":wave-" + wave);
        }
        if (lastRun == null) {
            throw new IllegalStateException(
                    "Job execution produced no TaskRun");
        }
        return lastRun;
    }

    /**
     * 并行执行同一 TaskGraph 层级的独立 Dispatch。
     *
     * @param dispatches Dispatch 批次
     * @return 执行结果
     */
    private List<ScheduledTaskResult> executeWave(
            List<JobRunScheduler.ScheduledTaskDispatch> dispatches) {
        try {
            return dispatches.stream()
                    .map(dispatch -> java.util.concurrent.CompletableFuture
                            .supplyAsync(
                                    () -> executeScheduledTask(dispatch),
                                    taskExecutor))
                    .toList()
                    .stream()
                    .map(java.util.concurrent.CompletableFuture::join)
                    .toList();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw failure;
        }
    }

    /**
     * 执行一个已物化运行骨架的 TaskRun，并由上层完成策略推进 TaskGraph。
     *
     * @param dispatch 持久化 Dispatch
     * @return TaskRun 与图推进结果
     */
    private ScheduledTaskResult executeScheduledTask(
            JobRunScheduler.ScheduledTaskDispatch dispatch) {
        RunExecutionContext context = dispatch.context();
        runScheduler.claimDispatch(dispatch.dispatchId(), workerId);
        boolean leaseAcquired = false;
        try {
            runtimeLeases.acquire(context.taskRunId());
            leaseAcquired = true;
            LoopOutcome outcome;
            try {
                outcome = loopRunExecutor.execute(context);
            } catch (LoopExecutionException failure) {
                LoopOutcome failedOutcome = transactions.fail(
                        failure.context(),
                        failure.originalFailure());
                completionCoordinator.reject(failedOutcome);
                throw failure.originalFailure();
            }
            if (outcome.status()
                    == LoopOutcome.OutcomeStatus.WAITING_CHILD_JOB) {
                // Job 层拥有结构变化：Loop 只请求，Coordinator 原子物化子 Job。
                childJobCoordinator.materialize(
                        context.jobId(),
                        context.taskRunId(),
                        context.loopRunId(),
                        context.loopNodeId(),
                        outcome.childJobRequest());
                ScheduledTaskResult result = new ScheduledTaskResult(
                        taskRunQueries.get(context.taskRunId()),
                        null);
                runScheduler.finishDispatch(
                        dispatch.dispatchId(),
                        true,
                        null);
                return result;
            }
            if (outcome.status()
                    == LoopOutcome.OutcomeStatus.WAITING_HUMAN) {
                ScheduledTaskResult result = new ScheduledTaskResult(
                        taskRunQueries.get(context.taskRunId()),
                        null);
                runScheduler.finishDispatch(
                        dispatch.dispatchId(),
                        true,
                        null);
                return result;
            }
            // Loop 只提交 Outcome；Job 层在独立事务中推进 Task 和 Job。
            var completionDecision = completionCoordinator.accept(outcome);
            if (completionDecision.jobCompleted()) {
                resumeParentIfChildJob(context.jobId());
            }
            ScheduledTaskResult result = new ScheduledTaskResult(
                    taskRunQueries.get(outcome.context().taskRunId()),
                    completionDecision);
            runScheduler.finishDispatch(
                    dispatch.dispatchId(),
                    true,
                    null);
            return result;
        } catch (RuntimeException failure) {
            runScheduler.finishDispatch(
                    dispatch.dispatchId(),
                    false,
                    failure);
            throw failure;
        } finally {
            if (leaseAcquired) {
                runtimeLeases.release(context.taskRunId());
            }
        }
    }

    /**
     * Child Job 通过 Job 验收后恢复 origin TaskRun。
     *
     * @param completedJobId 已完成 Job ID
     */
    private void resumeParentIfChildJob(UUID completedJobId) {
        var command = childJobCompletionCoordinator.prepare(completedJobId)
                .orElse(null);
        if (command == null) {
            return;
        }
        runtimeLeases.acquire(command.originTaskRunId());
        try {
            var origin = recoveryStore.requireLoopNodeResumeContext(
                    command.originLoopNodeId());
            LoopOutcome parentOutcome =
                    runtimeExecutionService.completeRecoveredChildJobAction(
                            origin,
                            command.outcome());
            if (parentOutcome.status()
                    == LoopOutcome.OutcomeStatus.WAITING_CHILD_JOB) {
                childJobCoordinator.materialize(
                        parentOutcome.context().jobId(),
                        parentOutcome.context().taskRunId(),
                        parentOutcome.context().loopRunId(),
                        parentOutcome.context().loopNodeId(),
                        parentOutcome.childJobRequest());
                return;
            }
            if (parentOutcome.status()
                    == LoopOutcome.OutcomeStatus.WAITING_HUMAN) {
                return;
            }
            var parentDecision = completionCoordinator.accept(parentOutcome);
            if (parentDecision.jobCompleted()) {
                // 递归 Child Job 链逐层回传，但最大深度受 Job 物化边界限制。
                resumeParentIfChildJob(command.parentJobId());
            }
        } catch (LoopExecutionException failure) {
            LoopOutcome failedOutcome = transactions.fail(
                    failure.context(),
                    failure.originalFailure());
            completionCoordinator.reject(failedOutcome);
            throw failure.originalFailure();
        } finally {
            runtimeLeases.release(command.originTaskRunId());
        }
    }

    /**
     * 关闭本地虚拟线程 Worker 池。
     */
    @PreDestroy
    public void closeTaskExecutor() {
        taskExecutor.close();
    }

    /**
     * 单个 TaskRun 完成后的同步调度结果。
     *
     * @param taskRun 已完成 TaskRun
     * @param completionDecision TaskGraph 推进结果
     */
    private record ScheduledTaskResult(
            TaskRunView taskRun,
            JobCompletionCoordinator.CompletionDecision completionDecision) {

        /**
         * @return 当前结果是否完成整个 Job
         */
        private boolean jobCompleted() {
            return completionDecision != null
                    && completionDecision.jobCompleted();
        }
    }
}
