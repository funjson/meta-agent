package com.funjson.metaagent.conversation.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Conversation 消息视图。
 *
 * @param id 消息 ID
 * @param role 角色
 * @param messageType 消息类型
 * @param content 内容
 * @param jobId 关联 Job
 * @param taskRunId 关联 TaskRun
 * @param createdAt 创建时间
 */
public record MessageView(
        UUID id,
        String role,
        String messageType,
        String content,
        UUID jobId,
        UUID taskRunId,
        Instant createdAt) {
}
