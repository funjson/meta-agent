package com.funjson.metaagent.tool.api;

import java.util.Map;

/**
 * 调用 Tool 的 HTTP 请求。
 *
 * @param arguments Tool 参数
 */
public record InvokeToolRequest(Map<String, Object> arguments) {
}
