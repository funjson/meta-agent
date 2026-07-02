package com.funjson.metaagent.intent.domain;

/**
 * Structured understanding for one complete user turn.
 *
 * <p>It is the formal home for mixed-intent handling, task-level rewrite and
 * turn-level orchestration. The primary representation is now a
 * {@link TurnIntentGraph}; the legacy {@link #actions()} view remains as a
 * compatibility helper while Control is migrated to graph execution.</p>
 *
 * @param graph semantic orchestration graph for this turn
 * @param auditSummary auditable summary of the whole understanding
 */
public record TurnUnderstanding(
        TurnIntentGraph graph,
        String auditSummary) {

    /**
     * Creates an understanding from a legacy action list.
     *
     * @param actions ordered Control actions derived from the user turn
     * @param auditSummary auditable summary of the whole understanding
     */
    public TurnUnderstanding(
            java.util.List<TurnAction> actions,
            String auditSummary) {
        this(TurnIntentGraph.fromActions(actions, auditSummary), auditSummary);
    }

    /**
     * Normalizes the graph and summary.
     */
    public TurnUnderstanding {
        if (graph == null) {
            throw new IllegalArgumentException(
                    "Turn understanding requires a graph");
        }
        auditSummary = auditSummary == null || auditSummary.isBlank()
                ? graph.auditSummary()
                : auditSummary.trim();
    }

    /**
     * @return legacy executable actions in graph node order
     */
    public java.util.List<TurnAction> actions() {
        return graph.actions();
    }

    /**
     * Converts the understanding into the executor-facing routing plan.
     *
     * @return Control routing plan
     */
    public TurnRoutingPlan toRoutingPlan() {
        return new TurnRoutingPlan(graph, auditSummary);
    }
}
