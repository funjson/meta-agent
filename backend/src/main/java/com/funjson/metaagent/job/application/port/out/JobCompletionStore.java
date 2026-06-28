package com.funjson.metaagent.job.application.port.out;

import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.task.domain.TaskStatus;

/**
 * 定义 Job 层提交 Task/Job 终态所需的持久化端口。
 */
public interface JobCompletionStore {

    /** 锁定 Job，使并行 Task 完成事务串行收敛。 */
    void lockJob(UUID jobId);

    /** 更新 Task 状态。 */
    void updateTaskStatus(UUID taskId, TaskStatus status);

    /** 更新 Job 状态。 */
    void updateJobStatus(UUID jobId, JobStatus status);

    /** @return 所有前置 Task 已完成的 BLOCKED Task */
    List<UUID> findUnblockedTaskIds(UUID jobId);

    /** @return 尚未完成的 Task 数量 */
    long countIncompleteTasks(UUID jobId);

    /** @return 当前 READY Task 数量 */
    long countReadyTasks(UUID jobId);
}
