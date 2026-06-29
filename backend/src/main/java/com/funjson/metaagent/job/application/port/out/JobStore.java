package com.funjson.metaagent.job.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.job.api.JobView;
import com.funjson.metaagent.task.api.TaskView;
import com.funjson.metaagent.job.domain.JobCreationContext;
import com.funjson.metaagent.job.domain.JobReplayCandidate;
import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.task.domain.TaskStatus;

/**
 * 定义 Job Application 创建和查询 Job/Task 所需的持久化端口。
 */
public interface JobStore {

    /** @return 幂等命令已关联的资源 ID */
    Optional<UUID> findResourceIdByIdempotencyKey(
            String idempotencyKey,
            String commandType);

    /** 插入 Job。 */
    void insertJob(
            UUID id,
            String originalRequest,
            String goalSummary,
            String providerId,
            JobStatus status,
            JobCreationContext context);

    /** 插入 Task Graph 节点。 */
    void insertTask(
            UUID taskId,
            UUID jobId,
            String taskKey,
            int sequenceNo,
            String title,
            String goal,
            TaskStatus status,
            String executionMode);

    /** 插入 Task Graph 依赖边。 */
    void insertTaskDependency(
            UUID taskId,
            UUID dependsOnTaskId);

    /** 插入运行事件。 */
    void insertRuntimeEvent(
            UUID eventId,
            UUID jobId,
            UUID taskId,
            String eventType,
            String payloadJson);

    /** 插入 Outbox 事件。 */
    void insertOutboxEvent(
            UUID eventId,
            String eventType,
            String payloadJson);

    /** 注册命令幂等键。 */
    void registerIdempotencyKey(
            String idempotencyKey,
            String commandType,
            UUID resourceId);

    /** @return Job 视图 */
    Optional<JobView> findById(UUID id);

    /** @return 指定父 Job 的直接子 Job */
    List<JobView> findChildren(UUID parentJobId);

    /** @return Job 分页数据 */
    List<JobView> findAll(int limit, int offset);

    /** @return 已提交但尚未物化 TaskRun 的可重放 Job */
    List<JobReplayCandidate> findStartableJobsForReplay(int limit);

    /** @return Job 总数 */
    long countAll();

    /** @return Job 下的 Task */
    List<TaskView> findTasksByJobId(UUID jobId);

    /** 将等待澄清的 Task 恢复为 READY。 */
    void resumeTaskAfterClarification(
            UUID jobId,
            UUID taskId,
            String answer,
            String extractedFactsJson,
            String answerSummary);
}
