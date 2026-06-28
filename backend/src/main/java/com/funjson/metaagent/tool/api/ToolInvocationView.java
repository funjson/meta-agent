package com.funjson.metaagent.tool.api;

import java.util.Map;
import java.util.UUID;

/**
 * Tool 调用审计视图。
 *
 * @param id 调用 ID
 * @param toolId Tool ID
 * @param status 状态
 * @param summary 结果摘要
 * @param result 结构化结果
 */
public record ToolInvocationView(
        UUID id,
        String toolId,
        String status,
        String summary,
        Map<String, Object> result) {
}
