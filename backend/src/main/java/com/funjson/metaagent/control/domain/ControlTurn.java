package com.funjson.metaagent.control.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 一条用户消息触发的可审计控制轮次。
 *
 * @param id ControlTurn ID
 * @param conversationId Conversation ID
 * @param sourceMessageId 来源用户消息
 * @param idempotencyKey 幂等键
 * @param status 当前状态
 * @param jobId 可选 Job ID
 * @param version 乐观锁版本
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record ControlTurn(
        UUID id,
        UUID conversationId,
        UUID sourceMessageId,
        String idempotencyKey,
        ControlTurnStatus status,
        UUID jobId,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 校验控制轮次身份和幂等边界。
     */
    public ControlTurn {
        if (id == null
                || conversationId == null
                || sourceMessageId == null) {
            throw new IllegalArgumentException(
                    "ControlTurn identity is required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "ControlTurn idempotency key is required");
        }
        if (status == null) {
            throw new IllegalArgumentException(
                    "ControlTurn status is required");
        }
    }
}
