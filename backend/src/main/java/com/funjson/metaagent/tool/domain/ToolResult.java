package com.funjson.metaagent.tool.domain;

import java.util.Map;
import java.util.UUID;

/**
 * 工具调用完成后写回 Loop Observation 的结果。
 *
 * @param invocationId 调用 ID
 * @param success 是否成功
 * @param summary 结果摘要
 * @param content 结果内容
 * @param attributes 结构化属性
 */
public record ToolResult(
        UUID invocationId,
        boolean success,
        String summary,
        String content,
        Map<String, Object> attributes) {

    /**
     * 复制属性集合。
     */
    public ToolResult {
        attributes = attributes == null
                ? Map.of()
                : Map.copyOf(attributes);
    }
}
