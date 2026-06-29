package com.funjson.metaagent.control.application;

import java.util.UUID;

import com.funjson.metaagent.context.domain.ContextEnvelope;
import com.funjson.metaagent.control.api.ChatTurnRequest;
import com.funjson.metaagent.conversation.api.ConversationView;

/**
 * Immutable context used while executing a Control turn routing plan.
 *
 * @param conversation conversation snapshot
 * @param controlTurnId ControlTurn ID
 * @param userMessageId source user message ID
 * @param idempotencyKey user message idempotency key
 * @param content normalized user content
 * @param request original chat request
 * @param envelope conversation context envelope
 * @param modelClassificationAllowed whether model-based control routing is allowed
 */
public record ControlTurnExecutionContext(
        ConversationView conversation,
        UUID controlTurnId,
        UUID userMessageId,
        String idempotencyKey,
        String content,
        ChatTurnRequest request,
        ContextEnvelope envelope,
        boolean modelClassificationAllowed) {
}
