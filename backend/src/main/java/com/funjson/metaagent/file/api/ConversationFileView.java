package com.funjson.metaagent.file.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Conversation 文件附件 API 视图。
 *
 * @param id 文件 ID
 * @param conversationId Conversation ID
 * @param fileName 用户可见文件名
 * @param contentType 内容类型
 * @param sizeBytes 文件大小
 * @param checksumSha256 内容 SHA-256
 * @param status 状态
 * @param createdAt 创建时间
 */
public record ConversationFileView(
        UUID id,
        UUID conversationId,
        String fileName,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String status,
        Instant createdAt) {
}
