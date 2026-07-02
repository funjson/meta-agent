package com.funjson.metaagent.intent.domain;

import java.util.List;

/**
 * One semantic task fragment inside a user-turn intent graph.
 *
 * <p>The node owns its source text, task type, risk labels and executable
 * action. This scoping is the key difference from the old mixed-turn action
 * list: sibling nodes must not leak labels, clarification contracts or risk
 * levels into each other.</p>
 *
 * @param nodeId stable node ID inside the current turn
 * @param nodeKind semantic node kind
 * @param taskType stable task category
 * @param sourceSpan exact user-message fragment for this node
 * @param canonicalGoal normalized node-level goal
 * @param labels node-scoped labels
 * @param action executable action compiled from this semantic node
 */
public record TurnIntentNode(
        String nodeId,
        TurnIntentNodeKind nodeKind,
        TurnTaskType taskType,
        String sourceSpan,
        String canonicalGoal,
        List<String> labels,
        TurnAction action) {

    /**
     * Normalizes node fields and derives missing node metadata.
     */
    public TurnIntentNode {
        nodeId = nodeId == null ? "" : nodeId.trim();
        nodeKind = nodeKind == null ? inferKind(action) : nodeKind;
        taskType = taskType == null ? TurnTaskType.infer(action) : taskType;
        sourceSpan = sourceSpan == null ? "" : sourceSpan.trim();
        canonicalGoal = canonicalGoal == null ? "" : canonicalGoal.trim();
        labels = labels == null ? List.of() : List.copyOf(labels);
        if (action == null) {
            throw new IllegalArgumentException(
                    "TurnIntentNode requires an executable action");
        }
    }

    /**
     * Creates a graph node from an existing linear action.
     *
     * @param index one-based action index
     * @param action executable action
     * @return graph node
     */
    public static TurnIntentNode fromAction(int index, TurnAction action) {
        String nodeId = "node-" + index;
        List<String> labels = action.recognition() == null
                ? List.of()
                : action.recognition().labels();
        return new TurnIntentNode(
                nodeId,
                inferKind(action),
                TurnTaskType.infer(action),
                action.sourceSpan(),
                action.canonicalGoal(),
                labels,
                action);
    }

    /**
     * Infers the graph node kind from the executable action type.
     */
    private static TurnIntentNodeKind inferKind(TurnAction action) {
        if (action == null) {
            return TurnIntentNodeKind.CHAT_RESPONSE;
        }
        return switch (action.actionType()) {
            case ANSWER_PENDING -> TurnIntentNodeKind.ANSWER_PENDING;
            case CREATE_JOB -> TurnIntentNodeKind.NEW_JOB;
            case ASK_DISAMBIGUATION -> TurnIntentNodeKind.DISAMBIGUATION;
            case EXPLAIN_PENDING_REQUIREMENTS ->
                    TurnIntentNodeKind.EXPLAIN_PENDING_REQUIREMENTS;
            case CONTROL_MESSAGE -> TurnIntentNodeKind.CONTROL_COMMAND;
            case CLARIFICATION_NO_TARGET ->
                    TurnIntentNodeKind.CLARIFICATION_NO_TARGET;
        };
    }
}
