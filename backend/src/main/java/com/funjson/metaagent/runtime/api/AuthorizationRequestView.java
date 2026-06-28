package com.funjson.metaagent.runtime.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 待授权请求视图。
 */
public record AuthorizationRequestView(
        UUID id,
        UUID jobId,
        UUID taskRunId,
        UUID loopNodeId,
        String requestType,
        String sourceType,
        String sourceId,
        Map<String, Object> requestedDelta,
        String status,
        Map<String, Object> decision,
        Instant createdAt,
        Instant decidedAt) {
}
