package com.funjson.metaagent.intent.domain;

import java.util.List;
import java.util.UUID;

/**
 * Input for TurnRouter.
 *
 * @param userMessage normalized user message
 * @param promptView conversation context prompt view
 * @param activeJobId currently active Job, if any
 * @param pendingCandidates open pending interaction candidates
 * @param modelRoutingAllowed whether model routing is allowed
 */
public record TurnRoutingRequest(
        String userMessage,
        String promptView,
        UUID activeJobId,
        List<PendingInteractionCandidate> pendingCandidates,
        boolean modelRoutingAllowed) {

    /** Copies collection fields and normalizes text. */
    public TurnRoutingRequest {
        userMessage = userMessage == null ? "" : userMessage.trim();
        promptView = promptView == null ? "" : promptView;
        pendingCandidates = pendingCandidates == null
                ? List.of()
                : List.copyOf(pendingCandidates);
    }
}
