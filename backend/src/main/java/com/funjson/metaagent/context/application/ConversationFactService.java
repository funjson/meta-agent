package com.funjson.metaagent.context.application;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.funjson.metaagent.context.application.port.out.ConversationFactStore;
import org.springframework.stereotype.Service;

/**
 * Writes reusable structured facts at Conversation scope.
 */
@Service
public class ConversationFactService {

    private static final String DEFAULT_SCOPE = "CONVERSATION";
    private static final double DEFAULT_CONFIDENCE = 0.86;
    private static final Set<String> SYSTEM_ONLY_FACTS = Set.of(
            "userAcceptedDefaults",
            "noSpecialRequirements");

    private final ConversationFactStore store;

    /**
     * Creates a Conversation fact service.
     *
     * @param store fact persistence port
     */
    public ConversationFactService(ConversationFactStore store) {
        this.store = store;
    }

    /**
     * Persists reusable facts extracted from a user message.
     *
     * <p>Operational flags such as default consent are useful for the current
     * clarification contract, but should not become long-lived profile facts.</p>
     *
     * @param conversationId Conversation ID
     * @param sourceMessageId source user message ID
     * @param sourceType source subsystem
     * @param facts extracted facts
     */
    public void rememberFacts(
            UUID conversationId,
            UUID sourceMessageId,
            String sourceType,
            Map<String, String> facts) {
        if (facts == null || facts.isEmpty()) {
            return;
        }
        facts.forEach((key, value) -> {
            if (ignored(key, value)) {
                return;
            }
            store.upsert(
                    UUID.randomUUID(),
                    conversationId,
                    sourceMessageId,
                    sourceType,
                    DEFAULT_SCOPE,
                    key.trim(),
                    value.trim(),
                    DEFAULT_CONFIDENCE);
        });
    }

    /**
     * Returns whether a fact should not be persisted as reusable context.
     */
    private boolean ignored(String key, String value) {
        return key == null
                || key.isBlank()
                || value == null
                || value.isBlank()
                || SYSTEM_ONLY_FACTS.contains(key.trim());
    }
}
