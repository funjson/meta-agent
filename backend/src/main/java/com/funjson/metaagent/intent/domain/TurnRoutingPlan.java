package com.funjson.metaagent.intent.domain;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Structured routing plan for one user message.
 *
 * <p>The plan sits above Job/TaskGraph/Loop. It describes what the Control
 * layer should do with this user turn before any new execution Job is started.</p>
 *
 * @param actions ordered Control actions
 * @param auditSummary auditable summary of the full routing decision
 */
public record TurnRoutingPlan(
        List<TurnAction> actions,
        String auditSummary) {

    /**
     * Copies actions and derives an audit summary when absent.
     */
    public TurnRoutingPlan {
        actions = actions == null ? List.of() : List.copyOf(actions);
        if (actions.isEmpty()) {
            throw new IllegalArgumentException(
                    "TurnRoutingPlan requires at least one action");
        }
        auditSummary = auditSummary == null || auditSummary.isBlank()
                ? actions.stream()
                        .map(action -> action.actionType().name())
                        .collect(Collectors.joining(" -> "))
                : auditSummary.trim();
    }

    /**
     * Creates a single-action plan.
     */
    public static TurnRoutingPlan single(TurnAction action) {
        return new TurnRoutingPlan(List.of(action), action.auditSummary());
    }

    /**
     * @return whether this plan contains more than one Control action
     */
    public boolean mixed() {
        return actions.size() > 1;
    }
}
