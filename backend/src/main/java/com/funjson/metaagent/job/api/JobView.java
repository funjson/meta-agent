package com.funjson.metaagent.job.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.task.api.TaskView;

/**
 * Job 的 API 只读视图。
 *
 * @param id Job ID
 * @param parentJobId 父 Job ID
 * @param rootJobId 根 Job ID
 * @param recursionDepth 递归深度
 * @param originalRequest 原始请求
 * @param goalSummary 目标摘要
 * @param providerId Provider ID
 * @param status 状态
 * @param version 乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param templateId 模板 ID
 * @param templateVersion 模板版本
 * @param templateKey 模板 Key
 * @param subagentProfileId SubagentProfile ID
 * @param subagentProfileVersion SubagentProfile 版本
 * @param tasks Task 列表
 * @param childJobs 直接子 Job
 */
public record JobView(
        UUID id,
        UUID parentJobId,
        UUID rootJobId,
        int recursionDepth,
        String originalRequest,
        String goalSummary,
        String providerId,
        JobStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        UUID templateId,
        Integer templateVersion,
        String templateKey,
        String subagentProfileId,
        Integer subagentProfileVersion,
        List<TaskView> tasks,
        List<JobView> childJobs) {

    /**
     * 复制集合字段。
     */
    public JobView {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        childJobs = childJobs == null
                ? List.of()
                : List.copyOf(childJobs);
    }

    /**
     * 返回附带 Task 的新视图。
     *
     * @param taskViews Task 列表
     * @return 新 JobView
     */
    public JobView withTasks(List<TaskView> taskViews) {
        return new JobView(
                id,
                parentJobId,
                rootJobId,
                recursionDepth,
                originalRequest,
                goalSummary,
                providerId,
                status,
                version,
                createdAt,
                updatedAt,
                templateId,
                templateVersion,
                templateKey,
                subagentProfileId,
                subagentProfileVersion,
                taskViews,
                childJobs);
    }

    /**
     * 返回附带直接子 Job 的新视图。
     *
     * @param children 直接子 Job
     * @return 新 JobView
     */
    public JobView withChildJobs(List<JobView> children) {
        return new JobView(
                id,
                parentJobId,
                rootJobId,
                recursionDepth,
                originalRequest,
                goalSummary,
                providerId,
                status,
                version,
                createdAt,
                updatedAt,
                templateId,
                templateVersion,
                templateKey,
                subagentProfileId,
                subagentProfileVersion,
                tasks,
                children);
    }
}
