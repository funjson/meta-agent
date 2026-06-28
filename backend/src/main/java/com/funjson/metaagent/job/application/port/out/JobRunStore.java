package com.funjson.metaagent.job.application.port.out;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.job.domain.LockedJobSnapshot;
import com.funjson.metaagent.task.domain.LockedTaskSnapshot;
import com.funjson.metaagent.task.domain.TaskStatus;
import com.funjson.metaagent.loop.application.port.out.RuntimeStore;

/**
 * 定义 Job 层初始化 TaskRun 时独占的锁定和上层状态写入端口。
 *
 * <p>Loop Application 只能依赖 RuntimeStore，因此无法修改 Job/Task 状态。</p>
 */
public interface JobRunStore extends RuntimeStore {

    /** @return 幂等启动命令关联的 TaskRun */
    Optional<UUID> findCommandResource(
            String idempotencyKey,
            String commandType);

    /** @return 加锁 Job 快照 */
    LockedJobSnapshot lockJob(UUID jobId);

    /** @return 加锁 READY Task 快照 */
    LockedTaskSnapshot lockReadyTask(UUID jobId);

    /** @return 加锁的 READY Task 批次 */
    List<LockedTaskSnapshot> lockReadyTasks(UUID jobId, int limit);

    /** @return 下一个 TaskRun 尝试序号 */
    int nextAttemptNo(UUID taskId);

    /** 更新 Job 状态。 */
    void updateJobStatus(UUID jobId, JobStatus status);

    /** 更新 Task 状态及活跃 TaskRun。 */
    void updateTaskStatus(
            UUID taskId,
            TaskStatus status,
            UUID activeTaskRunId);

    /** 插入 TaskRun。 */
    void insertTaskRun(UUID taskRunId, UUID taskId, int attemptNo);

    /** 插入持久化 TaskRun Dispatch。 */
    void insertTaskRunDispatch(
            UUID dispatchId,
            UUID jobId,
            UUID taskId,
            UUID taskRunId);

    /** @return 是否成功领取 Dispatch */
    boolean claimTaskRunDispatch(
            UUID dispatchId,
            String workerId);

    /** 完成或失败 Dispatch。 */
    void finishTaskRunDispatch(
            UUID dispatchId,
            String status,
            String lastError);

    /** 注册启动命令幂等键。 */
    void registerCommand(
            String idempotencyKey,
            String commandType,
            UUID resourceId);
}
