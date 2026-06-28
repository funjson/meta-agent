package com.funjson.metaagent.common.api;

import java.time.Instant;
import java.util.Map;

/**
 * REST API 的统一错误响应。
 *
 * @param code 稳定错误码
 * @param message 安全错误消息
 * @param timestamp 发生时间
 * @param details 非敏感错误详情
 */
public record ApiError(
        String code,
        String message,
        Instant timestamp,
        Map<String, Object> details) {
}
