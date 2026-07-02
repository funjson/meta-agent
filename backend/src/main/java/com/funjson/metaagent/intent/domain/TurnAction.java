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
        String auditSummary,
        String sourceSpan,
        String originalText,
        String canonicalGoal,
        IntentRewrite rewrite) {

    /**
     * Backward-compatible constructor for actions that do not carry a
     * dedicated source span or rewritten goal.
     *
     * @param actionType action kind
     * @param targetId optional pending interaction target ID
     * @param answerText optional answer text for pending interactions
     * @param facts structured facts extracted from user text
     * @param recognition optional intent recognition backing this action
     * @param userFacingMessage optional user-facing message
     * @param auditSummary audit summary for Agent Path and ControlDecision
     */
    public TurnAction(
            TurnActionType actionType,
            UUID targetId,
            String answerText,
            PendingInteractionFacts facts,
            IntentRecognition recognition,
            String userFacingMessage,
            String auditSummary) {
        this(
                actionType,
                targetId,
                answerText,
                facts,
                recognition,
                userFacingMessage,
                auditSummary,
                "",
                "",
                "",
                IntentRewrite.none());
    }

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
        sourceSpan = sourceSpan == null ? "" : sourceSpan.trim();
        originalText = originalText == null ? "" : originalText.trim();
        canonicalGoal = canonicalGoal == null ? "" : canonicalGoal.trim();
        rewrite = rewrite == null ? IntentRewrite.none() : rewrite;
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
        return createJob(
                recognition,
                "",
                "",
                "",
                IntentRewrite.none());
    }

    /**
     * Creates an action that creates a Job from a rewritten task goal.
     *
     * @param recognition intent recognition backing the Job
     * @param sourceSpan source text span from the user turn
     * @param originalText original text used for this action
     * @param canonicalGoal normalized task-level goal
     * @param rewrite rewrite audit metadata
     * @return executable CREATE_JOB action
     */
    public static TurnAction createJob(
            IntentRecognition recognition,
            String sourceSpan,
            String originalText,
            String canonicalGoal,
            IntentRewrite rewrite) {
        return new TurnAction(
                TurnActionType.CREATE_JOB,
                null,
                "",
                PendingInteractionFacts.empty(),
                recognition,
                "",
                recognition.decisionSummary(),
                sourceSpan,
                originalText,
                canonicalGoal,
                rewrite);
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

    /**
     * Returns the stable text that should initialize a Job.
     *
     * @param fallback full user turn text when no canonical goal exists
     * @return canonical goal, action original text, or fallback in that order
     */
    public String jobRequestText(String fallback) {
        if (!canonicalGoal.isBlank()) {
            return canonicalGoal;
        }
        if (!originalText.isBlank()) {
            return originalText;
        }
        return fallback == null ? "" : fallback.trim();
    }
}
