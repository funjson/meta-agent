package com.funjson.metaagent.conversation.api;

import jakarta.validation.constraints.Size;

/**
 * 创建 Conversation 的请求。
 *
 * @param agentProfileId AgentProfile ID
 * @param providerId 默认 Provider
 */
public record CreateConversationRequest(
        @Size(max = 80, message = "agentProfileId is too long")
        String agentProfileId,
        @Size(max = 50, message = "providerId is too long")
        String providerId) {
}
