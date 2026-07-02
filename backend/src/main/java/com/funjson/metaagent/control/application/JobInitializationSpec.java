package com.funjson.metaagent.control.application;

import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.intent.domain.TurnTaskType;

/**
 * Node-scoped input for creating a root Job.
 *
 * <p>The spec is compiled from one {@code TurnIntentNode}. It prevents a mixed
 * user turn from sharing labels, risk, canonical goals or idempotency keys
 * between unrelated Jobs.</p>
 *
 * @param nodeId source turn graph node ID
 * @param sourceSpan exact user text span for the node
 * @param originalText original node text used for audit
 * @param canonicalGoal normalized task-level goal
 * @param taskType stable turn task type
 * @param recognition node-scoped intent recognition
 */
public record JobInitializationSpec(
        String nodeId,
        String sourceSpan,
        String originalText,
        String canonicalGoal,
        TurnTaskType taskType,
        IntentRecognition recognition) {

    /**
     * Normalizes fields and verifies the required recognition.
     */
    public JobInitializationSpec {
        nodeId = nodeId == null ? "" : nodeId.trim();
        sourceSpan = sourceSpan == null ? "" : sourceSpan.trim();
        originalText = originalText == null ? "" : originalText.trim();
        canonicalGoal = canonicalGoal == null ? "" : canonicalGoal.trim();
        taskType = taskType == null ? TurnTaskType.UNKNOWN : taskType;
        if (recognition == null) {
            throw new IllegalArgumentException(
                    "JobInitializationSpec requires recognition");
        }
    }

    /**
     * Returns the stable text used to initialize the Job.
     *
     * @param fallback full user message when the node carries no goal text
     * @return canonical goal, original text, source span, or fallback
     */
    public String requestText(String fallback) {
        if (!canonicalGoal.isBlank()) {
            return canonicalGoal;
        }
        if (!originalText.isBlank()) {
            return originalText;
        }
        if (!sourceSpan.isBlank()) {
            return sourceSpan;
        }
        return fallback == null ? "" : fallback.trim();
    }
}
