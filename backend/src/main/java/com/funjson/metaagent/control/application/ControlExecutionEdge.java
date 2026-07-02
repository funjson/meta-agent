package com.funjson.metaagent.control.application;

/**
 * Directed executable relation between two Control execution nodes.
 *
 * @param fromNodeId source node ID
 * @param toNodeId target node ID
 * @param relationType executable relation type
 * @param reason auditable reason copied from the intent graph
 */
public record ControlExecutionEdge(
        String fromNodeId,
        String toNodeId,
        ControlExecutionRelationType relationType,
        String reason) {

    /**
     * Normalizes edge fields.
     */
    public ControlExecutionEdge {
        fromNodeId = fromNodeId == null ? "" : fromNodeId.trim();
        toNodeId = toNodeId == null ? "" : toNodeId.trim();
        relationType = relationType == null
                ? ControlExecutionRelationType.INDEPENDENT
                : relationType;
        reason = reason == null ? "" : reason.trim();
    }
}
