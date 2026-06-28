package com.funjson.metaagent.job.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.job.application.port.out.JobCompletionStore;
import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.task.domain.TaskStatus;
import org.springframework.stereotype.Repository;

/**
 * 适配 Job Completion 领域操作与 MyBatis Mapper。
 */
@Repository
public class JobCompletionRepository implements JobCompletionStore {

    private final JobCompletionMapper mapper;

    /**
     * 创建 Job Completion Repository。
     *
     * @param mapper MyBatis Mapper
     */
    public JobCompletionRepository(JobCompletionMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 锁定 Job 完成边界。
     *
     * @param jobId Job ID
     */
    public void lockJob(UUID jobId) {
        if (mapper.lockJob(jobId) == null) {
            throw new com.funjson.metaagent.job.domain.JobNotFoundException(
                    jobId);
        }
    }

    /**
     * 更新 Task 状态。
     *
     * @param taskId Task ID
     * @param status 目标状态
     */
    public void updateTaskStatus(UUID taskId, TaskStatus status) {
        mapper.updateTaskStatus(taskId, status.name());
    }

    /**
     * 更新 Job 状态。
     *
     * @param jobId Job ID
     * @param status 目标状态
     */
    public void updateJobStatus(UUID jobId, JobStatus status) {
        mapper.updateJobStatus(jobId, status.name());
    }

    /**
     * 查询依赖已经满足的 BLOCKED Task。
     *
     * @param jobId Job ID
     * @return 可提升 Task ID
     */
    public List<UUID> findUnblockedTaskIds(UUID jobId) {
        return mapper.findUnblockedTaskIds(jobId).stream()
                .map(UUID::fromString)
                .toList();
    }

    /**
     * 统计尚未完成的 Task。
     *
     * @param jobId Job ID
     * @return 未完成数量
     */
    public long countIncompleteTasks(UUID jobId) {
        return mapper.countIncompleteTasks(jobId);
    }

    /**
     * 统计 READY Task。
     *
     * @param jobId Job ID
     * @return READY 数量
     */
    public long countReadyTasks(UUID jobId) {
        return mapper.countReadyTasks(jobId);
    }
}
