package com.funjson.metaagent.intent.domain;

import java.util.UUID;

/**
 * A single executable Control action inside one user turn.
 *
 * @param actionType action kind
 * @param targetId optional pending interaction target ID
 * @param answerText optional answer text for pending interactions
 * @param facts structured facts extracted from user text
 * @param recognition optional intent recognition backing this action
 * @param userFacingMessage optional user-facing message
 * @param auditSummary audit summary for Agent Path and ControlDecision
 */
public record TurnAction(
        TurnActionType actionType,
        UUID targetId,
        String answerText,
        PendingInteractionFacts facts,
        IntentRecognition recognition,
        String userFacingMessage,
        String auditSummary) {

    /**
     * Normalizes nullable textual and fact fields.
     */
    public TurnAction {
        answerText = answerText == null ? "" : answerText.trim();
        facts = facts == null ? PendingInteractionFacts.empty() : facts;
        userFacingMessage = userFacingMessage == null
                ? ""
                : userFacingMessage.trim();
        auditSummary = auditSummary == null ? "" : auditSummary.trim();
    }

    /**
     * Creates an action that answers a pending interaction.
     */
    public static TurnAction answerPending(
            UUID targetId,
            String answerText,
            PendingInteractionFacts facts,
            String auditSummary) {
        return new TurnAction(
                TurnActionType.ANSWER_PENDING,
                targetId,
                answerText,
                facts,
                null,
                "",
                auditSummary);
    }

    /**
     * Creates an action that creates a Job from an intent recognition.
     */
    public static TurnAction createJob(IntentRecognition recognition) {
        return new TurnAction(
                TurnActionType.CREATE_JOB,
                null,
                "",
                PendingInteractionFacts.empty(),
                recognition,
                "",
                recognition.decisionSummary());
    }

    /**
     * Creates a Control-only action backed by an intent recognition.
     */
    public static TurnAction controlMessage(IntentRecognition recognition) {
        return new TurnAction(
                TurnActionType.CONTROL_MESSAGE,
                null,
                "",
                PendingInteractionFacts.empty(),
                recognition,
                recognition.decisionSummary(),
                recognition.decisionSummary());
    }
}
