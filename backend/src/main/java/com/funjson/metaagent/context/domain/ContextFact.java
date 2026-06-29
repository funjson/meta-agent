package com.funjson.metaagent.context.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A structured fact known at Conversation scope.
 *
 * @param id fact ID
 * @param conversationId Conversation ID
 * @param sourceMessageId source user message ID
 * @param sourceType source subsystem
 * @param scope fact scope
 * @param key stable fact key
 * @param value fact value
 * @param confidence extraction confidence
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
public record ContextFact(
        UUID id,
        UUID conversationId,
        UUID sourceMessageId,
        String sourceType,
        String scope,
        String key,
        String value,
        double confidence,
        Instant createdAt,
        Instant updatedAt) {
}
