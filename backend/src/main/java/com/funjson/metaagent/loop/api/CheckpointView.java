package com.funjson.metaagent.loop.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Checkpoint 的 API 视图。
 *
 * @param id Checkpoint ID
 * @param sequenceNo 序号
 * @param checkpointType 类型
 * @param restorable 是否可恢复
 * @param checksum 校验和
 * @param checksumValid 校验是否通过
 * @param createdAt 创建时间
 */
public record CheckpointView(
        UUID id,
        long sequenceNo,
        String checkpointType,
        boolean restorable,
        String checksum,
        boolean checksumValid,
        Instant createdAt) {
}
