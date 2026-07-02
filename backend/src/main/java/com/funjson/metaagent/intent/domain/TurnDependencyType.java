package com.funjson.metaagent.intent.domain;

/**
 * Relationship between two nodes in a single user-turn intent graph.
 */
public enum TurnDependencyType {
    /** Nodes can run independently; the edge is only explanatory. */
    INDEPENDENT(false),
    /** The downstream node needs the upstream node's final result. */
    DEPENDS_ON_RESULT(true),
    /** The downstream node must wait for ordering reasons, not data flow. */
    MUST_RUN_AFTER(true),
    /** A pending-answer node belongs to a target interaction. */
    ANSWERS_PENDING(false),
    /** Nodes conflict and require user disambiguation before execution. */
    CONFLICTS_WITH(true),
    /** The graph itself needs user disambiguation before downstream work. */
    NEEDS_DISAMBIGUATION(true);

    private final boolean blocksDownstream;

    /**
     * Creates a dependency type.
     *
     * @param blocksDownstream whether an unfinished source blocks the target
     */
    TurnDependencyType(boolean blocksDownstream) {
        this.blocksDownstream = blocksDownstream;
    }

    /**
     * @return whether a blocked source node prevents the target node running
     */
    public boolean blocksDownstream() {
        return blocksDownstream;
    }
}
