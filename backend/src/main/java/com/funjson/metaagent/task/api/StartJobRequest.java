package com.funjson.metaagent.task.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 兼容入口启动 Job 的请求。
 *
 * @param expectedVersion Job 期望版本
 */
public record StartJobRequest(
        @NotNull(message = "expectedVersion is required")
        @Min(value = 0, message = "expectedVersion must not be negative")
        Long expectedVersion) {
}
