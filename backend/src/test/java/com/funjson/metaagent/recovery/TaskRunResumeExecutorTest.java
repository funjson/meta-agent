package com.funjson.metaagent.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.job.application.JobCompletionCoordinator;
import com.funjson.metaagent.task.api.TaskRunView;
import com.funjson.metaagent.task.application.TaskRunQueryService;
import com.funjson.metaagent.loop.application.RuntimeExecutionService;
import com.funjson.metaagent.loop.application.RuntimeTransactionService;
import com.funjson.metaagent.loop.domain.LoopActionType;
import com.funjson.metaagent.loop.domain.LoopOutcome;
import com.funjson.metaagent.loop.domain.LoopRunParentType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.loop.infrastructure.persistence.mybatis.RuntimeRepository;
import com.funjson.metaagent.recovery.application.RuntimeLeaseService;
import com.funjson.metaagent.recovery.application.TaskRunResumeExecutor;
import com.funjson.metaagent.recovery.domain.InterruptionType;
import com.funjson.metaagent.recovery.domain.RecoveryCandidate;
import com.funjson.metaagent.recovery.domain.RecoveryDecision;
import com.funjson.metaagent.recovery.domain.RecoveryDisposition;
import com.funjson.metaagent.recovery.domain.RecoveryPolicy;
import com.funjson.metaagent.recovery.domain.ResumeExecutionSnapshot;
import com.funjson.metaagent.recovery.infrastructure.persistence.mybatis.RecoveryRepository;
import org.junit.jupiter.api.Test;

/**
 * 验证 ResumeExecutor 只在策略允许时获取租约并续跑。
 */
class TaskRunResumeExecutorTest {

    @Test
    void resumesPreparedModelActionAndAppliesJobCompletion() {
        Fixture fixture = fixture();
        RecoveryCandidate candidate = candidate();
        RunExecutionContext context = context(candidate);
        ResumeExecutionSnapshot snapshot = new ResumeExecutionSnapshot(
                candidate.checkpointId(),
                "ACTION_PREPARED",
                context,
                LoopActionType.MODEL_CALL,
                "content is present",
                512,
                UUID.randomUUID(),
                "RUNNING");
        LoopOutcome outcome = LoopOutcome.completed(
                context,
                "resumed result",
                UUID.randomUUID());
        TaskRunView taskRun = mock(TaskRunView.class);
        when(fixture.repository.requireCandidate(
                candidate.taskRunId())).thenReturn(candidate);
        when(fixture.policy.decide(candidate)).thenReturn(
                new RecoveryDecision(
                        InterruptionType.LEASE_EXPIRED,
                        RecoveryDisposition.AUTO_RESUME,
                        "SAFE_CHECKPOINT_AVAILABLE",
                        "safe"));
        when(fixture.repository.requireResumeSnapshot(
                candidate.taskRunId())).thenReturn(snapshot);
        when(fixture.runtimeExecution.resumePreparedModelAction(snapshot))
                .thenReturn(outcome);
        when(fixture.taskRunQueries.get(candidate.taskRunId()))
                .thenReturn(taskRun);

        TaskRunView result = fixture.executor.resume(
                candidate.taskRunId());

        assertThat(result).isSameAs(taskRun);
        verify(fixture.leases).acquire(candidate.taskRunId());
        verify(fixture.completion).accept(outcome);
        verify(fixture.leases).release(candidate.taskRunId());
        verify(fixture.repository).updateAttempt(
                any(UUID.class),
                eq("COMPLETED"),
                anyString());
    }

    @Test
    void rejectsResumeWhenPolicyRequiresReconciliation() {
        Fixture fixture = fixture();
        RecoveryCandidate candidate = candidate();
        when(fixture.repository.requireCandidate(
                candidate.taskRunId())).thenReturn(candidate);
        when(fixture.policy.decide(candidate)).thenReturn(
                new RecoveryDecision(
                        InterruptionType.UNKNOWN_SIDE_EFFECT,
                        RecoveryDisposition.RECONCILE_REQUIRED,
                        "SIDE_EFFECT_RECONCILIATION_REQUIRED",
                        "reconcile"));

        assertThatThrownBy(() -> fixture.executor.resume(
                candidate.taskRunId()))
                .isInstanceOf(RuntimeStateException.class)
                .extracting("code")
                .isEqualTo("SIDE_EFFECT_RECONCILIATION_REQUIRED");
    }

    @Test
    void marksAttemptFailedWhenLeaseRaceIsLost() {
        Fixture fixture = fixture();
        RecoveryCandidate candidate = candidate();
        RecoveryDecision decision = new RecoveryDecision(
                InterruptionType.LEASE_EXPIRED,
                RecoveryDisposition.AUTO_RESUME,
                "SAFE_CHECKPOINT_AVAILABLE",
                "safe");
        when(fixture.repository.requireCandidate(
                candidate.taskRunId())).thenReturn(candidate);
        when(fixture.policy.decide(candidate)).thenReturn(decision);
        doThrow(new RuntimeStateException(
                "TASK_RUN_LEASE_CONFLICT",
                "lease lost"))
                .when(fixture.leases)
                .acquire(candidate.taskRunId());

        assertThatThrownBy(() -> fixture.executor.resume(
                candidate.taskRunId()))
                .isInstanceOf(RuntimeStateException.class)
                .extracting("code")
                .isEqualTo("TASK_RUN_LEASE_CONFLICT");
        verify(fixture.repository).updateAttempt(
                any(UUID.class),
                eq("FAILED"),
                anyString());
    }

    /** 创建测试依赖。 */
    private Fixture fixture() {
        RecoveryRepository repository = mock(RecoveryRepository.class);
        RecoveryPolicy policy = mock(RecoveryPolicy.class);
        RuntimeLeaseService leases = mock(RuntimeLeaseService.class);
        RuntimeExecutionService runtimeExecution =
                mock(RuntimeExecutionService.class);
        RuntimeTransactionService transactions =
                mock(RuntimeTransactionService.class);
        TaskRunQueryService taskRunQueries =
                mock(TaskRunQueryService.class);
        JobCompletionCoordinator completion =
                mock(JobCompletionCoordinator.class);
        RuntimeRepository runtimeRepository =
                mock(RuntimeRepository.class);
        TaskRunResumeExecutor executor = new TaskRunResumeExecutor(
                repository,
                policy,
                leases,
                runtimeExecution,
                transactions,
                taskRunQueries,
                completion,
                runtimeRepository,
                new ObjectMapper());
        return new Fixture(
                repository,
                policy,
                leases,
                runtimeExecution,
                transactions,
                taskRunQueries,
                completion,
                executor);
    }

    /** 创建恢复候选。 */
    private RecoveryCandidate candidate() {
        return new RecoveryCandidate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "RUNNING",
                null,
                null,
                UUID.randomUUID(),
                "ACTION_PREPARED",
                true,
                true,
                UUID.randomUUID(),
                "TASK_RUN",
                UUID.randomUUID(),
                "RUNNING",
                "MODEL_CALL",
                "NONE");
    }

    /** 创建 Loop 上下文。 */
    private RunExecutionContext context(RecoveryCandidate candidate) {
        return new RunExecutionContext(
                candidate.jobId(),
                candidate.taskId(),
                candidate.taskRunId(),
                candidate.loopRunId(),
                candidate.loopNodeId(),
                null,
                0,
                1,
                LoopRunParentType.TASK_RUN,
                candidate.taskRunId(),
                0,
                "fake",
                "goal",
                "",
                null);
    }

    /**
     * 测试依赖集合。
     *
     * @param repository Recovery Repository
     * @param policy Recovery Policy
     * @param leases Lease Service
     * @param runtimeExecution Runtime Execution
     * @param transactions Runtime Transactions
     * @param taskRunQueries TaskRun 查询服务
     * @param completion Job Completion
     * @param executor Resume Executor
     */
    private record Fixture(
            RecoveryRepository repository,
            RecoveryPolicy policy,
            RuntimeLeaseService leases,
            RuntimeExecutionService runtimeExecution,
            RuntimeTransactionService transactions,
            TaskRunQueryService taskRunQueries,
            JobCompletionCoordinator completion,
            TaskRunResumeExecutor executor) {
    }
}
