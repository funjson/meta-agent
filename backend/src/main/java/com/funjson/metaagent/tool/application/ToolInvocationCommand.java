package com.funjson.metaagent.tool.application;

import java.util.Map;
import java.util.UUID;

/**
 * ToolRuntime 执行命令。
 *
 * @param toolId Tool ID
 * @param arguments 参数
 * @param idempotencyKey 幂等键
 * @param jobId 可选 Job ID
 * @param taskId 可选 Task ID
 * @param taskRunId 可选 TaskRun ID
 * @param loopRunId 可选 LoopRun ID
 * @param loopNodeId 可选 LoopNode ID
 */
public record ToolInvocationCommand(
        String toolId,
        Map<String, Object> arguments,
        String idempotencyKey,
        UUID jobId,
        UUID taskId,
        UUID taskRunId,
        UUID loopRunId,
        UUID loopNodeId) {

    /**
     * 复制参数。
     */
    public ToolInvocationCommand {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
