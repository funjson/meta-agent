package com.funjson.metaagent.loop.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 完成证据的 API 视图。
 *
 * @param id Evidence ID
 * @param evidenceType 类型
 * @param subjectRef 证据主体
 * @param result 结果
 * @param createdAt 创建时间
 */
public record EvidenceView(
        UUID id,
        String evidenceType,
        String subjectRef,
        String result,
        Instant createdAt) {
}
