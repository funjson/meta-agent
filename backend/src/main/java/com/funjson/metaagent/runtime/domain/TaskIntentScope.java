package com.funjson.metaagent.runtime.domain;

import java.util.List;
import java.util.Locale;

/**
 * Immutable task-scoped intent snapshot propagated from Control to Runtime.
 *
 * <p>A single user turn can produce multiple Jobs. Each Job must therefore
 * carry the exact TurnIntentNode boundary that created it: task type, local
 * source text, task-level goal, labels, clarification contract and tool
 * allowlist. Downstream TaskGraph, Context and Loop code should consume this
 * snapshot instead of re-inferring scope from the whole Conversation.</p>
 *
 * @param turnNodeId source TurnIntentNode ID
 * @param taskType stable task type name
 * @param sourceSpan original user text fragment for this node
 * @param originalText node-local original text
 * @param canonicalGoal node-local normalized goal
 * @param labels node-local labels
 * @param riskLevel node-local risk level
 * @param clarificationContractJson validated clarification contract JSON
 * @param allowedToolIds framework tool IDs visible to this task
 * @param specified whether this snapshot came from a formal TurnIntentNode
 */
public record TaskIntentScope(
        String turnNodeId,
        String taskType,
        String sourceSpan,
        String originalText,
        String canonicalGoal,
        List<String> labels,
        String riskLevel,
        String clarificationContractJson,
        List<String> allowedToolIds,
        boolean specified) {

    /**
     * Normalizes nullable values and copies collections.
     */
    public TaskIntentScope {
        turnNodeId = normalizeNullable(turnNodeId);
        taskType = normalizeNullable(taskType);
        sourceSpan = normalizeNullable(sourceSpan);
        originalText = normalizeNullable(originalText);
        canonicalGoal = normalizeNullable(canonicalGoal);
        labels = labels == null ? List.of() : List.copyOf(labels);
        riskLevel = normalizeNullable(riskLevel);
        clarificationContractJson = clarificationContractJson == null
                || clarificationContractJson.isBlank()
                        ? "{}"
                        : clarificationContractJson.trim();
        allowedToolIds = allowedToolIds == null
                ? List.of()
                : allowedToolIds.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
    }

    /**
     * Creates an unspecified scope for historical Jobs.
     *
     * @return empty scope that allows legacy heuristic fallback
     */
    public static TaskIntentScope unspecified() {
        return new TaskIntentScope(
                "",
                "UNKNOWN",
                "",
                "",
                "",
                List.of(),
                "",
                "{}",
                List.of(),
                false);
    }

    /**
     * Checks whether this scope explicitly allows one framework tool.
     *
     * @param toolId framework tool ID
     * @return true when the tool is in the task allowlist
     */
    public boolean allowsTool(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            return false;
        }
        String candidate = toolId.trim().toLowerCase(Locale.ROOT);
        return allowedToolIds.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(candidate::equals);
    }

    /**
     * @return task type normalized for comparisons
     */
    public String normalizedTaskType() {
        return taskType.toUpperCase(Locale.ROOT);
    }

    /**
     * Converts null text to an empty trimmed string.
     */
    private static String normalizeNullable(String value) {
        return value == null ? "" : value.trim();
    }
}
