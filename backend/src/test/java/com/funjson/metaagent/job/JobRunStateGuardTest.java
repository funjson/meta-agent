package com.funjson.metaagent.job;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.job.domain.LockedJobSnapshot;
import com.funjson.metaagent.task.domain.LockedTaskSnapshot;
import com.funjson.metaagent.job.domain.JobRunStateGuard;
import com.funjson.metaagent.task.domain.TaskStatus;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.junit.jupiter.api.Test;

/**
 * 验证 Job/Task 启动状态机不变量。
 */
class JobRunStateGuardTest {

    private final JobRunStateGuard guard = new JobRunStateGuard();

    @Test
    void permitsCreatedJobAndReadyTask() {
        var job = new LockedJobSnapshot(
                UUID.randomUUID(),
                JobStatus.CREATED,
                2,
                "fake",
                null);
        var task = new LockedTaskSnapshot(
                UUID.randomUUID(),
                "goal",
                "",
                TaskStatus.READY,
                0);

        assertThatCode(() -> {
            guard.requireJobStart(job, 2);
            guard.requireTaskReady(task);
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsStaleVersionBeforeExecution() {
        var job = new LockedJobSnapshot(
                UUID.randomUUID(),
                JobStatus.CREATED,
                3,
                "fake",
                null);

        assertThatThrownBy(() -> guard.requireJobStart(job, 2))
                .isInstanceOf(RuntimeStateException.class)
                .extracting(exception -> ((RuntimeStateException) exception).code())
                .isEqualTo("VERSION_CONFLICT");
    }

    @Test
    void rejectsCompletedJobRestart() {
        var job = new LockedJobSnapshot(
                UUID.randomUUID(),
                JobStatus.COMPLETED,
                2,
                "fake",
                null);

        assertThatThrownBy(() -> guard.requireJobStart(job, 2))
                .isInstanceOf(RuntimeStateException.class)
                .extracting(exception -> ((RuntimeStateException) exception).code())
                .isEqualTo("INVALID_STATE_TRANSITION");
    }
}
