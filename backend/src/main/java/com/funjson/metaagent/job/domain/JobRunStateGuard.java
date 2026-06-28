package com.funjson.metaagent.job.domain;

import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.job.domain.LockedJobSnapshot;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.task.domain.LockedTaskSnapshot;
import com.funjson.metaagent.task.domain.TaskStatus;

/**
 * 集中校验 Job/Task 启动时的状态机与乐观锁不变量。
 */
public class JobRunStateGuard {

    /**
     * 校验 Job 可以启动。
     *
     * @param job 加锁 Job
     * @param expectedVersion 期望版本
     */
    public void requireJobStart(
            LockedJobSnapshot job,
            long expectedVersion) {
        if (job.version() != expectedVersion) {
            throw new RuntimeStateException(
                    "VERSION_CONFLICT",
                    "Expected Job version %d but found %d".formatted(expectedVersion, job.version()));
        }
        if (job.status() != JobStatus.CREATED) {
            throw new RuntimeStateException(
                    "INVALID_STATE_TRANSITION",
                    "Job cannot start from " + job.status());
        }
    }

    /**
     * 校验已运行 Job 可以继续调度下一个 READY Task。
     *
     * @param job 加锁 Job
     */
    public void requireJobContinuation(LockedJobSnapshot job) {
        if (job.status() != JobStatus.RUNNING) {
            throw new RuntimeStateException(
                    "INVALID_STATE_TRANSITION",
                    "Job cannot schedule next task from "
                            + job.status());
        }
    }

    /**
     * 校验 Task 处于 READY。
     *
     * @param task 加锁 Task
     */
    public void requireTaskReady(LockedTaskSnapshot task) {
        if (task.status() != TaskStatus.READY) {
            throw new RuntimeStateException(
                    "INVALID_STATE_TRANSITION",
                    "Task cannot start from " + task.status());
        }
    }
}
