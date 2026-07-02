package com.funjson.metaagent.intent.domain;

/**
 * Directed relationship between two {@link TurnIntentNode}s.
 *
 * @param fromNodeId source node ID
 * @param toNodeId target node ID
 * @param relationType dependency relation type
 * @param reason auditable reason for the edge
 */
public record TurnIntentEdge(
        String fromNodeId,
        String toNodeId,
        TurnDependencyType relationType,
        String reason) {

    /**
     * Normalizes edge fields.
     */
    public TurnIntentEdge {
        fromNodeId = fromNodeId == null ? "" : fromNodeId.trim();
        toNodeId = toNodeId == null ? "" : toNodeId.trim();
        relationType = relationType == null
                ? TurnDependencyType.INDEPENDENT
                : relationType;
        reason = reason == null ? "" : reason.trim();
    }
}
