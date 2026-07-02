package com.funjson.metaagent.intent.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.funjson.metaagent.intent.domain.IntentType;
import com.funjson.metaagent.intent.domain.PendingInteractionCandidate;
import com.funjson.metaagent.intent.domain.TurnAction;
import com.funjson.metaagent.intent.domain.TurnActionType;
import com.funjson.metaagent.intent.domain.TurnDependencyType;
import com.funjson.metaagent.intent.domain.TurnIntentEdge;
import com.funjson.metaagent.intent.domain.TurnIntentNode;
import com.funjson.metaagent.intent.domain.TurnPlanViolation;
import com.funjson.metaagent.intent.domain.TurnRoutingRequest;
import com.funjson.metaagent.intent.domain.TurnUnderstanding;
import org.springframework.stereotype.Service;

/**
 * Validates model-generated turn understanding before Control executes it.
 *
 * <p>The validator protects execution state from stale pending targets,
 * unsupported actions and unsafe intent rewrites. It does not try to prove
 * semantic truth; that remains the model and later Job/Loop contracts' job.</p>
 */
@Service
public class TurnPlanValidator {

    private static final int MAX_NODES_PER_TURN = 8;

    /**
     * Validates and returns the same understanding when no violation exists.
     *
     * @param request original turn request
     * @param understanding model or fallback understanding
     * @return validated understanding
     */
    public TurnUnderstanding requireValid(
            TurnRoutingRequest request,
            TurnUnderstanding understanding) {
        List<TurnPlanViolation> violations = validate(
                request,
                understanding);
        if (!violations.isEmpty()) {
            TurnPlanViolation first = violations.getFirst();
            throw new IllegalArgumentException(
                    first.code() + ": " + first.summary());
        }
        return understanding;
    }

    /**
     * Validates a turn understanding and returns all detected violations.
     *
     * @param request original turn request
     * @param understanding candidate understanding
     * @return validation violations
     */
    public List<TurnPlanViolation> validate(
            TurnRoutingRequest request,
            TurnUnderstanding understanding) {
        List<TurnPlanViolation> violations = new ArrayList<>();
        if (understanding.graph().nodes().size() > MAX_NODES_PER_TURN) {
            violations.add(new TurnPlanViolation(
                    "TOO_MANY_TURN_NODES",
                    "A single turn cannot contain more than "
                            + MAX_NODES_PER_TURN
                            + " orchestration nodes"));
        }
        validateGraphShape(understanding, violations);
        Set<UUID> pendingIds = pendingIds(request.pendingCandidates());
        for (TurnIntentNode node : understanding.graph().nodes()) {
            validateAction(
                    request,
                    pendingIds,
                    node.action(),
                    violations);
        }
        return List.copyOf(violations);
    }

    /**
     * Validates graph IDs, edge endpoints and dependency cycles.
     */
    private void validateGraphShape(
            TurnUnderstanding understanding,
            List<TurnPlanViolation> violations) {
        Set<String> ids = new HashSet<>();
        for (TurnIntentNode node : understanding.graph().nodes()) {
            if (node.nodeId().isBlank()) {
                violations.add(new TurnPlanViolation(
                        "TURN_NODE_ID_EMPTY",
                        "Every turn graph node requires a nodeId"));
                continue;
            }
            if (!ids.add(node.nodeId())) {
                violations.add(new TurnPlanViolation(
                        "TURN_NODE_ID_DUPLICATED",
                        "Duplicated turn graph nodeId: " + node.nodeId()));
            }
        }
        Map<String, List<String>> blockingEdges = new HashMap<>();
        for (TurnIntentEdge edge : understanding.graph().edges()) {
            validateEdgeEndpoints(edge, ids, violations);
            if (edge.relationType().blocksDownstream()) {
                blockingEdges.computeIfAbsent(
                        edge.fromNodeId(),
                        ignored -> new ArrayList<>()).add(edge.toNodeId());
            }
        }
        if (hasCycle(blockingEdges)) {
            violations.add(new TurnPlanViolation(
                    "TURN_GRAPH_HAS_CYCLE",
                    "Blocking turn dependencies must form a DAG"));
        }
    }

    /**
     * Validates that a dependency edge references existing node IDs.
     */
    private void validateEdgeEndpoints(
            TurnIntentEdge edge,
            Set<String> nodeIds,
            List<TurnPlanViolation> violations) {
        if (edge.fromNodeId().equals(edge.toNodeId())
                && edge.relationType() != TurnDependencyType.INDEPENDENT) {
            violations.add(new TurnPlanViolation(
                    "TURN_EDGE_SELF_DEPENDENCY",
                    "A blocking turn edge cannot point to the same node"));
        }
        if (!nodeIds.contains(edge.fromNodeId())
                || !nodeIds.contains(edge.toNodeId())) {
            violations.add(new TurnPlanViolation(
                    "TURN_EDGE_NODE_MISSING",
                    "Turn graph edge references a missing node"));
        }
    }

    /**
     * Detects cycles in blocking dependency edges.
     */
    private boolean hasCycle(Map<String, List<String>> graph) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String nodeId : graph.keySet()) {
            if (visitForCycle(nodeId, graph, visiting, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Performs one depth-first cycle detection step.
     */
    private boolean visitForCycle(
            String nodeId,
            Map<String, List<String>> graph,
            Set<String> visiting,
            Set<String> visited) {
        if (visited.contains(nodeId)) {
            return false;
        }
        if (!visiting.add(nodeId)) {
            return true;
        }
        for (String next : graph.getOrDefault(nodeId, List.of())) {
            if (visitForCycle(next, graph, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
        return false;
    }

    /**
     * Validates one action according to its executable contract.
     */
    private void validateAction(
            TurnRoutingRequest request,
            Set<UUID> pendingIds,
            TurnAction action,
            List<TurnPlanViolation> violations) {
        if (action.actionType() == TurnActionType.CREATE_JOB) {
            validateCreateJob(
                    request,
                    action,
                    violations);
            return;
        }
        if (requiresPendingTarget(action.actionType())
                && (action.targetId() == null
                || !pendingIds.contains(action.targetId()))) {
            violations.add(new TurnPlanViolation(
                    "PENDING_TARGET_NOT_OPEN",
                    "Pending action target is not present in the current "
                            + "ContextEnvelope"));
        }
        if (action.actionType() == TurnActionType.ANSWER_PENDING
                && action.answerText().isBlank()) {
            violations.add(new TurnPlanViolation(
                    "PENDING_ANSWER_EMPTY",
                    "ANSWER_PENDING requires non-empty answer text"));
        }
        if ((action.actionType() == TurnActionType.CONTROL_MESSAGE
                || action.actionType() == TurnActionType.CLARIFICATION_NO_TARGET)
                && action.recognition() == null) {
            violations.add(new TurnPlanViolation(
                    "CONTROL_RECOGNITION_REQUIRED",
                    action.actionType().name()
                            + " requires an IntentRecognition"));
        }
    }

    /**
     * Validates a create-job action and its task-level rewrite.
     */
    private void validateCreateJob(
            TurnRoutingRequest request,
            TurnAction action,
            List<TurnPlanViolation> violations) {
        if (action.recognition() == null
                || !action.recognition().createsJob()) {
            violations.add(new TurnPlanViolation(
                    "CREATE_JOB_RECOGNITION_REQUIRED",
                    "CREATE_JOB requires an executable IntentRecognition"));
        }
        String jobText = action.jobRequestText(request.userMessage());
        if (jobText.isBlank()) {
            violations.add(new TurnPlanViolation(
                    "CREATE_JOB_GOAL_EMPTY",
                    "CREATE_JOB requires a canonical goal or source text"));
        }
        if (action.recognition() != null
                && action.recognition().intentType()
                == IntentType.CLARIFICATION_ANSWER) {
            violations.add(new TurnPlanViolation(
                    "CREATE_JOB_CLARIFICATION_ANSWER",
                    "CLARIFICATION_ANSWER cannot create a new Job"));
        }
        if (!action.canonicalGoal().isBlank()
                && looksLikeSearchQuery(action.canonicalGoal())) {
            violations.add(new TurnPlanViolation(
                    "QUERY_REWRITE_IN_INTENT_LAYER",
                    "Intent rewrite must produce a task goal, not a concrete "
                            + "search query"));
        }
    }

    /**
     * Checks if an action must reference an open pending interaction.
     */
    private boolean requiresPendingTarget(TurnActionType actionType) {
        return actionType == TurnActionType.ANSWER_PENDING
                || actionType == TurnActionType.EXPLAIN_PENDING_REQUIREMENTS;
    }

    /**
     * Builds a set of currently open pending IDs.
     */
    private Set<UUID> pendingIds(List<PendingInteractionCandidate> candidates) {
        Set<UUID> ids = new HashSet<>();
        for (PendingInteractionCandidate candidate : candidates) {
            ids.add(candidate.id());
        }
        return ids;
    }

    /**
     * Detects common query-plan shapes that belong to Research/Tool planning.
     */
    private boolean looksLikeSearchQuery(String value) {
        String normalized = value.toLowerCase();
        return normalized.contains("site:")
                || normalized.contains("intitle:")
                || normalized.contains(" inurl:")
                || normalized.startsWith("\"")
                || normalized.matches(".*\\b(or|and)\\b.*\\+.*");
    }
}
