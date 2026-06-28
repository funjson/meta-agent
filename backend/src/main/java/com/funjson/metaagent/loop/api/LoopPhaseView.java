package com.funjson.metaagent.loop.api;

import java.time.Instant;
import java.util.UUID;

import com.funjson.metaagent.loop.domain.LoopPhaseStatus;
import com.funjson.metaagent.loop.domain.LoopPhaseType;

/**
 * LoopNode 内部阶段的 API 视图。
 *
 * @param id 阶段记录 ID
 * @param phaseType 阶段类型
 * @param sequenceNo 节点内顺序
 * @param status 状态
 * @param summary 脱敏结构化摘要
 * @param startedAt 开始时间
 * @param completedAt 完成时间
 */
public record LoopPhaseView(
        UUID id,
        LoopPhaseType phaseType,
        int sequenceNo,
        LoopPhaseStatus status,
        String summary,
        Instant startedAt,
        Instant completedAt) {
}
