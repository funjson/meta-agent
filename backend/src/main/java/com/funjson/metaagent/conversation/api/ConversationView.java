package com.funjson.metaagent.conversation.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Conversation 及其消息的 API 视图。
 *
 * @param id Conversation ID
 * @param agentProfileId AgentProfile ID
 * @param title 标题
 * @param status 状态
 * @param defaultProviderId 默认 Provider
 * @param activeJobId 活跃 Job
 * @param version 版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param messages 消息列表
 */
public record ConversationView(
        UUID id,
        String agentProfileId,
        String title,
        String status,
        String defaultProviderId,
        UUID activeJobId,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<MessageView> messages) {

    /**
     * 返回附带消息的新 ConversationView。
     *
     * @param items 消息列表
     * @return 新视图
     */
    public ConversationView withMessages(List<MessageView> items) {
        return new ConversationView(
                id,
                agentProfileId,
                title,
                status,
                defaultProviderId,
                activeJobId,
                version,
                createdAt,
                updatedAt,
                List.copyOf(items));
    }
}
