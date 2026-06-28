package com.funjson.metaagent.control.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户发送一轮聊天消息的请求。
 *
 * @param content 消息内容
 * @param providerId 请求级 Provider
 */
public record ChatTurnRequest(
        @NotBlank(message = "content is required")
        @Size(max = 20_000, message = "content is too long")
        String content,
        @Size(max = 50, message = "providerId is too long")
        String providerId) {
}
