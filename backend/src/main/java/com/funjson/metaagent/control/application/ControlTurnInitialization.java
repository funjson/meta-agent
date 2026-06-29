package com.funjson.metaagent.control.application;

import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.control.api.ControlDecisionView;
import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.conversation.api.MessageView;
import com.funjson.metaagent.job.api.JobView;

/**
 * Result of initializing a Control turn.
 *
 * @param controlTurnId ControlTurn ID
 * @param conversation Conversation snapshot
 * @param userMessage source user message
 * @param decision persisted ControlDecision
 * @param job representative Job for the response, if any
 * @param resumeTaskRunId legacy single resume ID for simple callers
 * @param immediateAssistantMessage optional immediate user-visible message
 * @param immediateAssistantMessageType conversation message type
 * @param dispatches background execution dispatches to submit after commit
 */
public record ControlTurnInitialization(
        UUID controlTurnId,
        ConversationView conversation,
        MessageView userMessage,
        ControlDecisionView decision,
        JobView job,
        UUID resumeTaskRunId,
        String immediateAssistantMessage,
        String immediateAssistantMessageType,
        List<ControlDispatchCommand> dispatches) {

    /**
     * Creates an initialization result without an immediate message.
     */
    public ControlTurnInitialization(
            UUID controlTurnId,
            ConversationView conversation,
            MessageView userMessage,
            ControlDecisionView decision,
            JobView job,
            UUID resumeTaskRunId) {
        this(
                controlTurnId,
                conversation,
                userMessage,
                decision,
                job,
                resumeTaskRunId,
                null,
                null,
                List.of());
    }

    /**
     * Copies collection fields so the result is immutable.
     */
    public ControlTurnInitialization {
        dispatches = dispatches == null ? List.of() : List.copyOf(dispatches);
    }
}
