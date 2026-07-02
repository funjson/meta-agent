package com.funjson.metaagent.intent.domain;

import java.util.List;

/**
 * Structured routing plan for one user message.
 *
 * <p>The plan sits above Job/TaskGraph/Loop. It describes what the Control
 * layer should do with this user turn before any new execution Job is started.
 * Its primary contract is a {@link TurnIntentGraph}; the action-list view is
 * retained for existing execution code and tests.</p>
 *
 * @param graph turn-level semantic orchestration graph
 * @param auditSummary auditable summary of the full routing decision
 */
public record TurnRoutingPlan(
        TurnIntentGraph graph,
        String auditSummary) {

    /**
     * Creates a plan from a legacy action list.
     *
     * @param actions ordered Control actions
     * @param auditSummary auditable summary of the full routing decision
     */
    public TurnRoutingPlan(
            List<TurnAction> actions,
            String auditSummary) {
        this(TurnIntentGraph.fromActions(actions, auditSummary), auditSummary);
    }

    /**
     * Normalizes graph and summary.
     */
    public TurnRoutingPlan {
        if (graph == null) {
            throw new IllegalArgumentException(
                    "TurnRoutingPlan requires a TurnIntentGraph");
        }
        auditSummary = auditSummary == null || auditSummary.isBlank()
                ? graph.auditSummary()
                : auditSummary.trim();
    }

    /**
     * Creates a single-action plan.
     */
    public static TurnRoutingPlan single(TurnAction action) {
        return new TurnRoutingPlan(List.of(action), action.auditSummary());
    }

    /**
     * @return executable actions in graph node order
     */
    public List<TurnAction> actions() {
        return graph.actions();
    }

    /**
     * @return whether this plan contains more than one Control action
     */
    public boolean mixed() {
        return graph.mixed();
    }
}
