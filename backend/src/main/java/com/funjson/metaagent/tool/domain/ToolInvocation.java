package com.funjson.metaagent.tool.domain;

import java.util.UUID;

/**
 * LoopNode 计划执行的工具调用。
 *
 * @param id 调用 ID
 * @param toolName 工具名
 * @param argumentsJson 参数 JSON
 * @param idempotencyKey 幂等键
 * @param sourceLoopNodeId 来源 LoopNode
 */
public record ToolInvocation(
        UUID id,
        String toolName,
        String argumentsJson,
        String idempotencyKey,
        UUID sourceLoopNodeId) {
}
