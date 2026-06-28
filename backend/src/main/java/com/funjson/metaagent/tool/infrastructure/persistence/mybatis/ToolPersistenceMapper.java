package com.funjson.metaagent.tool.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ToolRuntime MyBatis Mapper。
 */
@Mapper
public interface ToolPersistenceMapper {

    /** @return 脚本工具行 */
    List<Map<String, Object>> findScriptTools();

    /** @return 脚本工具行 */
    Map<String, Object> findScriptTool(@Param("toolId") String toolId);

    /** @return Job 所属 Conversation ID */
    String findConversationIdByJobId(@Param("jobId") UUID jobId);

    /** @return ToolInvocation 行 */
    Map<String, Object> findInvocationByIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey);

    /** @return 插入行数 */
    int insertInvocation(
            @Param("invocationId") UUID invocationId,
            @Param("toolId") String toolId,
            @Param("toolType") String toolType,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("argumentsJson") String argumentsJson,
            @Param("jobId") UUID jobId,
            @Param("taskId") UUID taskId,
            @Param("taskRunId") UUID taskRunId,
            @Param("loopRunId") UUID loopRunId,
            @Param("loopNodeId") UUID loopNodeId);

    /** @return 更新行数 */
    int markRunning(@Param("invocationId") UUID invocationId);

    /** @return 更新行数 */
    int complete(
            @Param("invocationId") UUID invocationId,
            @Param("resultJson") String resultJson);

    /** @return 更新行数 */
    int attachClarification(
            @Param("invocationId") UUID invocationId,
            @Param("clarificationRequestId") UUID clarificationRequestId);

    /** @return 更新行数 */
    int fail(
            @Param("invocationId") UUID invocationId,
            @Param("errorMessage") String errorMessage,
            @Param("resultJson") String resultJson);
}
