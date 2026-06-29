package com.funjson.metaagent.intent.domain;

/**
 * Control action types produced by a TurnRoutingPlan.
 */
public enum TurnActionType {
    /** Answer an existing pending interaction or clarification. */
    ANSWER_PENDING,
    /** Create a new Job from the current user message. */
    CREATE_JOB,
    /** Ask the user to disambiguate which pending item should receive input. */
    ASK_DISAMBIGUATION,
    /** Explain what the current pending interaction still needs. */
    EXPLAIN_PENDING_REQUIREMENTS,
    /** Render a Control-only message without creating or resuming a Job. */
    CONTROL_MESSAGE,
    /** Protect against a clarification-like answer with no open target. */
    CLARIFICATION_NO_TARGET
}
