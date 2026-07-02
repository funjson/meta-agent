package com.funjson.metaagent.control.application;

/**
 * Executable dependency relation between Control execution nodes.
 */
public enum ControlExecutionRelationType {
    /** Nodes are independent; the edge is informational. */
    INDEPENDENT(false),
    /** Target node needs the source node's final result. */
    DEPENDS_ON_RESULT(true),
    /** Target node must run after the source node. */
    MUST_RUN_AFTER(true),
    /** Target node is blocked until the user disambiguates the source. */
    NEEDS_DISAMBIGUATION(true);

    private final boolean blocksDownstream;

    /**
     * Creates a relation type.
     *
     * @param blocksDownstream whether source blocking blocks target execution
     */
    ControlExecutionRelationType(boolean blocksDownstream) {
        this.blocksDownstream = blocksDownstream;
    }

    /**
     * @return whether source waiting state blocks the target node
     */
    public boolean blocksDownstream() {
        return blocksDownstream;
    }
}
