package com.funjson.metaagent.provider.domain;

import java.util.Map;

/**
 * 模型返回的原生工具调用请求。
 *
 * @param id Provider 返回的 ToolCall ID，可为空
 * @param toolId 平台内部稳定 Tool ID
 * @param functionName Provider 层函数名
 * @param arguments 结构化参数
 */
public record ModelToolCall(
        String id,
        String toolId,
        String functionName,
        Map<String, Object> arguments) {

    /**
     * 复制参数，避免下游修改模型响应。
     */
    public ModelToolCall {
        if (toolId == null || toolId.isBlank()) {
            throw new IllegalArgumentException("Tool id is required");
        }
        if (functionName == null || functionName.isBlank()) {
            throw new IllegalArgumentException("Function name is required");
        }
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
