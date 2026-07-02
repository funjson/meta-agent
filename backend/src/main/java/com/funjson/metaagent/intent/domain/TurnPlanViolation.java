package com.funjson.metaagent.intent.domain;

/**
 * Stable validation violation for a model-generated turn plan.
 *
 * @param code stable violation code
 * @param summary auditable violation summary
 */
public record TurnPlanViolation(
        String code,
        String summary) {

    /**
     * Normalizes nullable text.
     */
    public TurnPlanViolation {
        code = code == null || code.isBlank()
                ? "TURN_PLAN_INVALID"
                : code.trim();
        summary = summary == null ? "" : summary.trim();
    }
}
