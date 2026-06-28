package com.funjson.metaagent.job.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.funjson.metaagent.job.domain.JobCreationContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 定义 Job/Task Store 的 MyBatis SQL 映射。
 */
@Mapper
public interface JobPersistenceMapper {

    /**
     * 按幂等键查询已创建资源。
     *
     * @param idempotencyKey 幂等键
     * @param commandType 命令类型
     * @return UUID 字符串，不存在时返回 {@code null}
     */
    String findResourceIdByIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("commandType") String commandType);

    /**
     * 插入 Job。
     *
     * @param id Job ID
     * @param originalRequest 原始请求
     * @param goalSummary 目标摘要
     * @param status 初始状态
     * @param providerId Provider ID
     * @param context 创建上下文
     * @return 插入行数
     */
    int insertJob(
            @Param("id") UUID id,
            @Param("originalRequest") String originalRequest,
            @Param("goalSummary") String goalSummary,
            @Param("status") String status,
            @Param("providerId") String providerId,
            @Param("context") JobCreationContext context);

    /**
     * 插入 Job 的 Task Graph 节点。
     *
     * @param taskId Task ID
     * @param jobId Job ID
     * @param taskKey Job 内 Task Key
     * @param sequenceNo 稳定顺序
     * @param title 标题
     * @param goal 目标
     * @param status 状态
     * @param executionMode 执行模式
     * @return 插入行数
     */
    int insertTask(
            @Param("taskId") UUID taskId,
            @Param("jobId") UUID jobId,
            @Param("taskKey") String taskKey,
            @Param("sequenceNo") int sequenceNo,
            @Param("title") String title,
            @Param("goal") String goal,
            @Param("status") String status,
            @Param("executionMode") String executionMode);

    /**
     * 插入 Task Graph 依赖边。
     *
     * @param taskId 下游 Task ID
     * @param dependsOnTaskId 前置 Task ID
     * @return 插入行数
     */
    int insertTaskDependency(
            @Param("taskId") UUID taskId,
            @Param("dependsOnTaskId") UUID dependsOnTaskId);

    /**
     * 插入 Job 创建事件。
     *
     * @param eventId 事件 ID
     * @param jobId Job ID
     * @param taskId Task ID
     * @param eventType 事件类型
     * @param payloadJson 事件负载
     * @return 插入行数
     */
    int insertRuntimeEvent(
            @Param("eventId") UUID eventId,
            @Param("jobId") UUID jobId,
            @Param("taskId") UUID taskId,
            @Param("eventType") String eventType,
            @Param("payloadJson") String payloadJson);

    /**
     * 插入 Outbox 事件。
     *
     * @param eventId 事件 ID
     * @param eventType 事件类型
     * @param payloadJson 事件负载
     * @return 插入行数
     */
    int insertOutboxEvent(
            @Param("eventId") UUID eventId,
            @Param("eventType") String eventType,
            @Param("payloadJson") String payloadJson);

    /**
     * 注册幂等命令。
     *
     * @param idempotencyKey 幂等键
     * @param commandType 命令类型
     * @param resourceId 资源 ID
     * @return 插入行数
     */
    int registerIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("commandType") String commandType,
            @Param("resourceId") UUID resourceId);

    /**
     * 查询 Job 基础行。
     *
     * @param id Job ID
     * @return 数据库行
     */
    Map<String, Object> findJobById(@Param("id") UUID id);

    /**
     * 查询直接子 Job。
     *
     * @param parentJobId 父 Job ID
     * @return 子 Job 行
     */
    List<Map<String, Object>> findChildJobs(
            @Param("parentJobId") UUID parentJobId);

    /**
     * 分页查询 Job 基础行。
     *
     * @param limit 返回数量
     * @param offset 偏移量
     * @return 数据库行
     */
    List<Map<String, Object>> findAllJobs(
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 统计 Job 数量。
     *
     * @return 总数
     */
    long countAllJobs();

    /**
     * 查询 Job 下的 Task 和最近运行摘要。
     *
     * @param jobId Job ID
     * @return Task 行
     */
    List<Map<String, Object>> findTasksByJobId(@Param("jobId") UUID jobId);

    /**
     * 将等待澄清的 Task 恢复为 READY。
     *
     * @param jobId Job ID
     * @param taskId Task ID
     * @param answer 用户回答
     * @param extractedFactsJson 抽取事实 JSON
     * @param answerSummary 系统审计摘要
     * @return 更新行数
     */
    int resumeTaskAfterClarification(
            @Param("jobId") UUID jobId,
            @Param("taskId") UUID taskId,
            @Param("answer") String answer,
            @Param("extractedFactsJson") String extractedFactsJson,
            @Param("answerSummary") String answerSummary);

    /**
     * 将等待澄清的 Job 恢复为可调度状态。
     *
     * @param jobId Job ID
     * @return 更新行数
     */
    int resumeJobAfterClarification(@Param("jobId") UUID jobId);
}
