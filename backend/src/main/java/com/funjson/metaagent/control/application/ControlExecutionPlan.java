package com.funjson.metaagent.control.application;

import java.util.List;
import java.util.Set;

/**
 * Executable Control plan compiled from a turn-intent graph.
 *
 * @param nodes executable nodes in model/user order
 * @param edges executable dependency edges
 * @param auditSummary auditable summary shown in ControlDecision/Agent Path
 */
public record ControlExecutionPlan(
        List<ControlExecutionNode> nodes,
        List<ControlExecutionEdge> edges,
        String auditSummary) {

    /**
     * Copies collections and normalizes text.
     */
    public ControlExecutionPlan {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "ControlExecutionPlan requires at least one node");
        }
        auditSummary = auditSummary == null ? "" : auditSummary.trim();
    }

    /**
     * Checks whether a node is blocked by any previously blocked upstream node.
     *
     * @param nodeId candidate node ID
     * @param blockedNodeIds nodes that entered waiting/disambiguation state
     * @return true when a blocking upstream relation exists
     */
    public boolean blockedByUpstream(
            String nodeId,
            Set<String> blockedNodeIds) {
        if (blockedNodeIds == null || blockedNodeIds.isEmpty()) {
            return false;
        }
        return edges.stream()
                .anyMatch(edge -> edge.toNodeId().equals(nodeId)
                        && edge.relationType().blocksDownstream()
                        && blockedNodeIds.contains(edge.fromNodeId()));
    }
}
