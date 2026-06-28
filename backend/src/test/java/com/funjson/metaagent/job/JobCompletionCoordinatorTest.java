package com.funjson.metaagent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.job.application.JobCompletionCoordinator;
import com.funjson.metaagent.job.application.port.out.JobCompletionStore;
import com.funjson.metaagent.job.domain.DefaultJobCompletionPolicy;
import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.task.domain.DefaultTaskCompletionPolicy;
import com.funjson.metaagent.task.domain.TaskStatus;
import com.funjson.metaagent.loop.application.port.out.RuntimeStore;
import com.funjson.metaagent.loop.domain.LoopOutcome;
import com.funjson.metaagent.loop.domain.LoopRunParentType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import org.junit.jupiter.api.Test;

/**
 * 验证 Job 层验收一个 Task 后推进依赖图，而不是提前完成整个 Job。
 */
class JobCompletionCoordinatorTest {

    @Test
    void promotesUnblockedTaskAndKeepsJobRunning() {
        Fixture fixture = fixture();
        UUID nextTaskId = UUID.randomUUID();
        when(fixture.store().findUnblockedTaskIds(
                fixture.context().jobId()))
                .thenReturn(List.of(nextTaskId));
        when(fixture.store().countIncompleteTasks(
                fixture.context().jobId())).thenReturn(1L);
        when(fixture.store().countReadyTasks(
                fixture.context().jobId())).thenReturn(1L);

        var decision = fixture.service().accept(
                completed(fixture.context()));

        assertThat(decision.jobCompleted()).isFalse();
        assertThat(decision.hasReadyTask()).isTrue();
        verify(fixture.store()).updateTaskStatus(
                nextTaskId,
                TaskStatus.READY);
        verify(fixture.store(), never()).updateJobStatus(
                fixture.context().jobId(),
                JobStatus.COMPLETED);
    }

    @Test
    void completesJobOnlyAfterLastTask() {
        Fixture fixture = fixture();
        when(fixture.store().findUnblockedTaskIds(
                fixture.context().jobId()))
                .thenReturn(List.of());
        when(fixture.store().countIncompleteTasks(
                fixture.context().jobId())).thenReturn(0L);
        when(fixture.store().countReadyTasks(
                fixture.context().jobId())).thenReturn(0L);

        var decision = fixture.service().accept(
                completed(fixture.context()));

        assertThat(decision.jobCompleted()).isTrue();
        verify(fixture.store()).updateJobStatus(
                fixture.context().jobId(),
                JobStatus.COMPLETED);
    }

    /** 创建 Job Completion 测试依赖。 */
    private Fixture fixture() {
        JobCompletionStore store =
                mock(JobCompletionStore.class);
        RuntimeStore runtimeStore = mock(RuntimeStore.class);
        RunExecutionContext context = context();
        return new Fixture(
                new JobCompletionCoordinator(
                        store,
                        runtimeStore,
                        new ObjectMapper(),
                        new DefaultTaskCompletionPolicy(),
                        new DefaultJobCompletionPolicy()),
                store,
                context);
    }

    /** 创建成功 LoopOutcome。 */
    private LoopOutcome completed(RunExecutionContext context) {
        return LoopOutcome.completed(
                context,
                "done",
                UUID.randomUUID());
    }

    /** 创建测试运行上下文。 */
    private RunExecutionContext context() {
        UUID taskRunId = UUID.randomUUID();
        return new RunExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                taskRunId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                0,
                1,
                LoopRunParentType.TASK_RUN,
                taskRunId,
                0,
                "fake",
                "goal",
                "",
                null);
    }

    /**
     * 测试依赖集合。
     *
     * @param service Job Completion Coordinator
     * @param store Completion Store
     * @param context 运行上下文
     */
    private record Fixture(
            JobCompletionCoordinator service,
            JobCompletionStore store,
            RunExecutionContext context) {
    }
}
