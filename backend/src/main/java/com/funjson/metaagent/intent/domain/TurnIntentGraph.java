package com.funjson.metaagent.intent.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Structured orchestration graph for one complete user turn.
 *
 * <p>This graph is above Job/TaskGraph/Loop. It answers: how many independent
 * or dependent things did the user say in this message, which pending
 * interactions are being answered, and which new Jobs should be created. It
 * deliberately does not create Jobs or mutate runtime state.</p>
 *
 * @param nodes semantic nodes extracted from the user turn
 * @param edges directed dependencies between nodes
 * @param auditSummary auditable summary of the full graph
 */
public record TurnIntentGraph(
        List<TurnIntentNode> nodes,
        List<TurnIntentEdge> edges,
        String auditSummary) {

    /**
     * Copies collections and verifies basic graph shape.
     */
    public TurnIntentGraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "TurnIntentGraph requires at least one node");
        }
        auditSummary = auditSummary == null || auditSummary.isBlank()
                ? defaultSummary(nodes)
                : auditSummary.trim();
    }

    /**
     * Builds a graph from the legacy linear action list.
     *
     * @param actions executable actions
     * @param auditSummary graph summary
     * @return graph with one node per action
     */
    public static TurnIntentGraph fromActions(
            List<TurnAction> actions,
            String auditSummary) {
        List<TurnAction> safeActions = actions == null
                ? List.of()
                : List.copyOf(actions);
        List<TurnIntentNode> nodes = IntStream.range(0, safeActions.size())
                .mapToObj(index -> TurnIntentNode.fromAction(
                        index + 1,
                        safeActions.get(index)))
                .toList();
        return new TurnIntentGraph(nodes, List.of(), auditSummary);
    }

    /**
     * @return executable actions in node order
     */
    public List<TurnAction> actions() {
        return nodes.stream()
                .map(TurnIntentNode::action)
                .toList();
    }

    /**
     * @return true when the graph contains multiple semantic nodes
     */
    public boolean mixed() {
        return nodes.size() > 1;
    }

    /**
     * @return all known node IDs
     */
    public Set<String> nodeIds() {
        Set<String> result = new HashSet<>();
        for (TurnIntentNode node : nodes) {
            result.add(node.nodeId());
        }
        return Set.copyOf(result);
    }

    /**
     * Builds a compact fallback summary from node action names.
     */
    private static String defaultSummary(List<TurnIntentNode> nodes) {
        return nodes.stream()
                .map(node -> node.action().actionType().name())
                .reduce((left, right) -> left + " -> " + right)
                .orElse("Turn intent graph");
    }
}
