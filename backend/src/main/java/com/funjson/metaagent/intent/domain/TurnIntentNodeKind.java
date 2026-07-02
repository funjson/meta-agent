package com.funjson.metaagent.intent.domain;

/**
 * Semantic node kinds inside one user turn.
 *
 * <p>A node kind describes what the Control layer should eventually do with a
 * user-message fragment. It is intentionally above Job/TaskGraph/Loop so mixed
 * user turns can be reasoned about before any execution state is mutated.</p>
 */
public enum TurnIntentNodeKind {
    /** Create a new root Job from this node. */
    NEW_JOB,
    /** Bind this node's text and facts to an open pending interaction. */
    ANSWER_PENDING,
    /** Return a direct conversational answer without creating a Job. */
    CHAT_RESPONSE,
    /** Execute a Control-only command such as pause, resume or status. */
    CONTROL_COMMAND,
    /** Ask the user which pending target or task fragment they meant. */
    DISAMBIGUATION,
    /** Explain what an existing pending interaction still needs. */
    EXPLAIN_PENDING_REQUIREMENTS,
    /** Protect against a clarification-like answer with no resumable target. */
    CLARIFICATION_NO_TARGET
}
