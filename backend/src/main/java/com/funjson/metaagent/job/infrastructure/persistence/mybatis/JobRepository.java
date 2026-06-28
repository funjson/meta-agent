package com.funjson.metaagent.job.infrastructure.persistence.mybatis;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.job.api.JobView;
import com.funjson.metaagent.task.api.TaskView;
import com.funjson.metaagent.job.application.port.out.JobStore;
import com.funjson.metaagent.job.domain.JobCreationContext;
import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.task.domain.TaskStatus;
import org.springframework.stereotype.Repository;

/**
 * 适配 Job Application 与 Job/Task MyBatis Mapper。
 */
@Repository
public class JobRepository implements JobStore {

    private final JobPersistenceMapper mapper;

    /**
     * 创建 Job Repository Adapter。
     *
     * @param mapper Job/Task MyBatis Mapper
     */
    public JobRepository(JobPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询已处理幂等命令的资源 ID。
     *
     * @param idempotencyKey 幂等键
     * @param commandType 命令类型
     * @return 已存在资源 ID
     */
    public Optional<UUID> findResourceIdByIdempotencyKey(
            String idempotencyKey,
            String commandType) {
        return Optional.ofNullable(mapper.findResourceIdByIdempotencyKey(
                        idempotencyKey,
                        commandType))
                .map(UUID::fromString);
    }

    /**
     * 插入 Job 聚合根。
     *
     * @param id Job ID
     * @param originalRequest 原始请求
     * @param goalSummary 目标摘要
     * @param providerId Provider ID
     * @param status 初始 Job 状态
     * @param context 创建上下文
     */
    public void insertJob(
            UUID id,
            String originalRequest,
            String goalSummary,
            String providerId,
            JobStatus status,
            JobCreationContext context) {
        mapper.insertJob(
                id,
                originalRequest,
                goalSummary,
                status.name(),
                providerId,
                context);
    }

    /**
     * 插入 Task Graph 节点。
     *
     * @param taskId Task ID
     * @param jobId Job ID
     * @param taskKey Job 内 Task Key
     * @param sequenceNo 稳定顺序
     * @param title 标题
     * @param goal Task 目标
     * @param status 初始状态
     * @param executionMode 执行模式
     */
    public void insertTask(
            UUID taskId,
            UUID jobId,
            String taskKey,
            int sequenceNo,
            String title,
            String goal,
            TaskStatus status,
            String executionMode) {
        mapper.insertTask(
                taskId,
                jobId,
                taskKey,
                sequenceNo,
                title,
                goal,
                status.name(),
                executionMode);
    }

    /**
     * 插入 Task Graph 依赖边。
     *
     * @param taskId 下游 Task ID
     * @param dependsOnTaskId 前置 Task ID
     */
    public void insertTaskDependency(
            UUID taskId,
            UUID dependsOnTaskId) {
        mapper.insertTaskDependency(taskId, dependsOnTaskId);
    }

    /**
     * 插入运行事件。
     *
     * @param eventId 事件 ID
     * @param jobId Job ID
     * @param taskId Task ID
     * @param eventType 事件类型
     * @param payloadJson 事件负载
     */
    public void insertRuntimeEvent(
            UUID eventId,
            UUID jobId,
            UUID taskId,
            String eventType,
            String payloadJson) {
        mapper.insertRuntimeEvent(
                eventId,
                jobId,
                taskId,
                eventType,
                payloadJson);
    }

    /**
     * 插入 Outbox 事件。
     *
     * @param eventId 事件 ID
     * @param eventType 事件类型
     * @param payloadJson 事件负载
     */
    public void insertOutboxEvent(
            UUID eventId,
            String eventType,
            String payloadJson) {
        mapper.insertOutboxEvent(eventId, eventType, payloadJson);
    }

    /**
     * 注册命令幂等键。
     *
     * @param idempotencyKey 幂等键
     * @param commandType 命令类型
     * @param resourceId 资源 ID
     */
    public void registerIdempotencyKey(
            String idempotencyKey,
            String commandType,
            UUID resourceId) {
        mapper.registerIdempotencyKey(
                idempotencyKey,
                commandType,
                resourceId);
    }

    /**
     * 查询 Job。
     *
     * @param id Job ID
     * @return Job 视图
     */
    public Optional<JobView> findById(UUID id) {
        return Optional.ofNullable(mapper.findJobById(id)).map(this::toJobView);
    }

    /**
     * 查询直接子 Job。
     *
     * @param parentJobId 父 Job ID
     * @return 子 Job
     */
    public List<JobView> findChildren(UUID parentJobId) {
        return mapper.findChildJobs(parentJobId).stream()
                .map(this::toJobView)
                .toList();
    }

    /**
     * 分页查询 Job。
     *
     * @param limit 返回数量
     * @param offset 偏移量
     * @return Job 列表
     */
    public List<JobView> findAll(int limit, int offset) {
        return mapper.findAllJobs(limit, offset)
                .stream()
                .map(this::toJobView)
                .toList();
    }

    /**
     * 统计 Job 总数。
     *
     * @return 总数
     */
    public long countAll() {
        return mapper.countAllJobs();
    }

    /**
     * 查询 Job 下的 Task。
     *
     * @param jobId Job ID
     * @return Task 列表
     */
    public List<TaskView> findTasksByJobId(UUID jobId) {
        return mapper.findTasksByJobId(jobId).stream()
                .map(this::toTaskView)
                .toList();
    }

    /**
     * 将等待澄清的 Task 恢复为 READY。
     *
     * @param jobId Job ID
     * @param taskId Task ID
     * @param answer 澄清回答
     * @param extractedFactsJson 抽取事实 JSON
     * @param answerSummary 系统审计摘要
     */
    public void resumeTaskAfterClarification(
            UUID jobId,
            UUID taskId,
            String answer,
            String extractedFactsJson,
            String answerSummary) {
        String safeFactsJson = extractedFactsJson == null
                || extractedFactsJson.isBlank()
                        ? "{}"
                        : extractedFactsJson;
        int taskUpdated = mapper.resumeTaskAfterClarification(
                jobId,
                taskId,
                answer,
                safeFactsJson,
                answerSummary == null ? "" : answerSummary);
        if (taskUpdated != 1) {
            throw new RuntimeStateException(
                            "CLARIFICATION_RESUME_TARGET_INVALID",
                            "Clarification target cannot resume: "
                                    + taskId);
        }
        int jobUpdated = mapper.resumeJobAfterClarification(jobId);
        if (jobUpdated > 1) {
            throw new RuntimeStateException(
                    "CLARIFICATION_RESUME_JOB_INVALID",
                    "Clarification job resume affected unexpected rows: "
                            + jobId);
        }
    }

    /**
     * 将数据库行转换为 JobView。
     *
     * @param row 数据库行
     * @return JobView
     */
    private JobView toJobView(Map<String, Object> row) {
        return new JobView(
                UUID.fromString(text(row, "id")),
                uuid(row.get("parentJobId")),
                uuid(row.get("rootJobId")),
                number(row, "recursionDepth").intValue(),
                text(row, "originalRequest"),
                text(row, "goalSummary"),
                text(row, "providerId"),
                JobStatus.valueOf(text(row, "status")),
                number(row, "version").longValue(),
                instant(row.get("createdAt")),
                instant(row.get("updatedAt")),
                uuid(row.get("templateId")),
                row.get("templateVersion") == null
                        ? null
                        : number(row, "templateVersion").intValue(),
                nullableText(row.get("templateKey")),
                nullableText(row.get("subagentProfileId")),
                row.get("subagentProfileVersion") == null
                        ? null
                        : number(row, "subagentProfileVersion").intValue(),
                List.of(),
                List.of());
    }

    /**
     * 将数据库行转换为 TaskView。
     *
     * @param row 数据库行
     * @return TaskView
     */
    private TaskView toTaskView(Map<String, Object> row) {
        return new TaskView(
                UUID.fromString(text(row, "id")),
                text(row, "taskKey"),
                number(row, "sequenceNo").intValue(),
                text(row, "title"),
                text(row, "goal"),
                text(row, "taskType"),
                TaskStatus.valueOf(text(row, "status")),
                text(row, "executionMode"),
                uuid(row.get("latestTaskRunId")),
                nullableText(row.get("latestTaskRunStatus")),
                nullableText(row.get("resultSummary")),
                splitKeys(row.get("dependencyKeys")),
                number(row, "version").longValue());
    }

    /**
     * 将逗号分隔的稳定 Task Key 转换为依赖列表。
     *
     * @param value 数据库聚合列
     * @return 依赖 Task Key
     */
    private List<String> splitKeys(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        return List.of(String.valueOf(value).split(","));
    }

    /**
     * 读取必填字符串列。
     *
     * @param row 数据库行
     * @param key 列名
     * @return 字符串
     */
    private String text(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }

    /**
     * 读取可空字符串列。
     *
     * @param value 列值
     * @return 字符串或空
     */
    private String nullableText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 读取数值列。
     *
     * @param row 数据库行
     * @param key 列名
     * @return 数值
     */
    private Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    /**
     * 将 UUID 字符串列转换为 UUID。
     *
     * @param value 列值
     * @return UUID 或空
     */
    private UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(String.valueOf(value));
    }

    /**
     * 将数据库时间转换为 Instant。
     *
     * @param value 时间列
     * @return Instant
     */
    private Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.atZone(java.time.ZoneOffset.UTC).toInstant();
        }
        throw new IllegalArgumentException("Unsupported timestamp value: " + value);
    }
}
