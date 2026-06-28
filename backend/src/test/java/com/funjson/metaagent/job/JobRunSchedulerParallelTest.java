package com.funjson.metaagent.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.job.application.JobRunScheduler;
import com.funjson.metaagent.job.application.port.out.JobRunStore;
import com.funjson.metaagent.job.domain.JobRunStateGuard;
import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.job.domain.LockedJobSnapshot;
import com.funjson.metaagent.task.domain.LockedTaskSnapshot;
import com.funjson.metaagent.task.domain.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证同一 TaskGraph 层级可以物化多个独立 Dispatch。
 */
class JobRunSchedulerParallelTest {

    @Test
    void materializesAllReadyTasksInOneWave() {
        JobRunStore store = mock(JobRunStore.class);
        UUID jobId = UUID.randomUUID();
        when(store.findCommandResource(any(), any()))
                .thenReturn(Optional.empty());
        when(store.lockJob(jobId)).thenReturn(new LockedJobSnapshot(
                jobId,
                JobStatus.CREATED,
                0,
                "fake",
                null));
        when(store.lockReadyTasks(eq(jobId), anyInt()))
                .thenReturn(List.of(
                        task("first"),
                        task("second")));
        when(store.nextAttemptNo(any())).thenReturn(1);
        when(store.insertRuntimeEvent(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any())).thenReturn(1L);

        JobRunScheduler scheduler = new JobRunScheduler(
                store,
                new JobRunStateGuard(),
                new ObjectMapper());
        var batch = scheduler.beginWave(jobId, 0, "start");

        assertThat(batch.dispatches()).hasSize(2);
        verify(store, times(2)).insertTaskRunDispatch(
                any(),
                eq(jobId),
                any(),
                any());
    }

    /** 创建 READY Task。 */
    private LockedTaskSnapshot task(String goal) {
        return new LockedTaskSnapshot(
                UUID.randomUUID(),
                goal,
                "",
                TaskStatus.READY,
                0);
    }
}
