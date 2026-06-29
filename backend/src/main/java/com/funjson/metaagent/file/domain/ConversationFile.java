package com.funjson.metaagent.file.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Conversation 级受控文件附件。
 *
 * @param id 文件 ID
 * @param conversationId Conversation ID
 * @param fileName 用户可见文件名
 * @param storagePath 相对存储路径
 * @param contentType 内容类型
 * @param sizeBytes 文件大小
 * @param checksumSha256 内容 SHA-256
 * @param status 状态
 * @param createdAt 创建时间
 */
public record ConversationFile(
        UUID id,
        UUID conversationId,
        String fileName,
        String storagePath,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String status,
        Instant createdAt) {

    /**
     * 校验不可变文件元数据。
     */
    public ConversationFile {
        if (id == null || conversationId == null) {
            throw new IllegalArgumentException("File identity is required");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name is required");
        }
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("Storage path is required");
        }
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("File size must be positive");
        }
        status = status == null || status.isBlank() ? "ACTIVE" : status;
    }
}
