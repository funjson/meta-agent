package com.funjson.metaagent.job.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 兼容入口使用的创建 Job 请求。
 *
 * @param originalRequest 用户原始请求
 * @param providerId Provider ID
 */
public record CreateJobRequest(
        @NotBlank(message = "originalRequest is required")
        @Size(max = 20_000, message = "originalRequest is too long")
        String originalRequest,
        @Size(max = 50, message = "providerId is too long")
        String providerId) {
}
