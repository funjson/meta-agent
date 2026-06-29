package com.funjson.metaagent.context.infrastructure.persistence.mybatis;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.funjson.metaagent.context.application.port.out.ConversationFactStore;
import com.funjson.metaagent.context.domain.ContextFact;
import org.springframework.stereotype.Repository;

/**
 * MyBatis adapter for ConversationFactStore.
 */
@Repository
public class ConversationFactRepository implements ConversationFactStore {

    private final ConversationFactPersistenceMapper mapper;

    /**
     * Creates a repository.
     *
     * @param mapper MyBatis mapper
     */
    public ConversationFactRepository(
            ConversationFactPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public void upsert(
            UUID id,
            UUID conversationId,
            UUID sourceMessageId,
            String sourceType,
            String scope,
            String key,
            String value,
            double confidence) {
        mapper.upsert(
                id,
                conversationId,
                sourceMessageId,
                sourceType,
                scope,
                key,
                value,
                confidence);
    }

    /** {@inheritDoc} */
    @Override
    public List<ContextFact> findActiveByConversation(UUID conversationId) {
        return mapper.findActiveByConversation(conversationId)
                .stream()
                .map(this::toFact)
                .toList();
    }

    /**
     * Converts a database row to a domain fact.
     */
    private ContextFact toFact(Map<String, Object> row) {
        return new ContextFact(
                uuid(row.get("id")),
                uuid(row.get("conversationId")),
                nullableUuid(row.get("sourceMessageId")),
                text(row, "sourceType"),
                text(row, "scope"),
                text(row, "key"),
                text(row, "value"),
                ((Number) row.get("confidence")).doubleValue(),
                instant(row.get("createdAt")),
                instant(row.get("updatedAt")));
    }

    /** Reads required text. */
    private String text(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }

    /** Reads required UUID. */
    private UUID uuid(Object value) {
        return UUID.fromString(String.valueOf(value));
    }

    /** Reads nullable UUID. */
    private UUID nullableUuid(Object value) {
        return value == null ? null : uuid(value);
    }

    /** Reads a UTC instant from a database timestamp. */
    private Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        }
        throw new IllegalArgumentException(
                "Unsupported timestamp value: " + value);
    }
}
