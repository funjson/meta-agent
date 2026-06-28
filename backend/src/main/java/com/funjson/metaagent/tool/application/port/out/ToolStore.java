package com.funjson.metaagent.tool.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.tool.domain.ScriptToolSpec;
import com.funjson.metaagent.tool.domain.ToolDefinition;

/**
 * Tool 定义和调用审计持久化端口。
 */
public interface ToolStore {

    /** @return 已注册脚本工具定义 */
    List<ToolDefinition> findScriptToolDefinitions();

    /** @return 脚本工具规格 */
    Optional<ScriptToolSpec> findScriptTool(String toolId);

    /** @return Job 所属 Conversation ID */
    Optional<UUID> findConversationIdByJobId(UUID jobId);

    /** @return 幂等命中的调用视图行 */
    Optional<Map<String, Object>> findInvocationByIdempotencyKey(
            String idempotencyKey);

    /** 插入 ToolInvocation。 */
    void insertInvocation(
            UUID invocationId,
            String toolId,
            String toolType,
            String idempotencyKey,
            String argumentsJson,
            UUID jobId,
            UUID taskId,
            UUID taskRunId,
            UUID loopRunId,
            UUID loopNodeId);

    /** 标记 ToolInvocation 运行中。 */
    void markRunning(UUID invocationId);

    /** 标记 ToolInvocation 完成。 */
    void complete(
            UUID invocationId,
            String resultJson);

    /** 关联 ToolInvocation 与它创建的澄清请求。 */
    void attachClarification(
            UUID invocationId,
            UUID clarificationRequestId);

    /** 标记 ToolInvocation 失败。 */
    void fail(
            UUID invocationId,
            String errorMessage,
            String resultJson);
}
