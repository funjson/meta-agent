package com.funjson.metaagent.context.application.port.out;

import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.context.domain.ContextFact;

/**
 * Persistence port for Conversation-scoped structured facts.
 */
public interface ConversationFactStore {

    /**
     * Upserts one structured fact.
     *
     * @param id fact ID used on insert
     * @param conversationId Conversation ID
     * @param sourceMessageId source message ID
     * @param sourceType source subsystem
     * @param scope fact scope
     * @param key stable fact key
     * @param value fact value
     * @param confidence extraction confidence
     */
    void upsert(
            UUID id,
            UUID conversationId,
            UUID sourceMessageId,
            String sourceType,
            String scope,
            String key,
            String value,
            double confidence);

    /**
     * Finds active facts for a conversation.
     *
     * @param conversationId Conversation ID
     * @return active facts ordered by update time
     */
    List<ContextFact> findActiveByConversation(UUID conversationId);
}
