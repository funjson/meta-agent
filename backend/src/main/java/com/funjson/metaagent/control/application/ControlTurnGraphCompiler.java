package com.funjson.metaagent.control.application;

import java.util.List;

import com.funjson.metaagent.intent.domain.TurnDependencyType;
import com.funjson.metaagent.intent.domain.TurnIntentEdge;
import com.funjson.metaagent.intent.domain.TurnIntentNode;
import com.funjson.metaagent.intent.domain.TurnRoutingPlan;
import org.springframework.stereotype.Service;

/**
 * Compiles an Intent-layer turn graph into an executable Control plan.
 *
 * <p>The compiler is intentionally mechanical. Semantic decisions belong to
 * Intent; Job creation belongs to Job initialization. Control only needs a
 * durable execution order and dependency semantics.</p>
 */
@Service
public class ControlTurnGraphCompiler {

    /**
     * Compiles a routing plan into executable Control nodes and edges.
     *
     * @param plan validated turn routing plan
     * @return executable Control plan
     */
    public ControlExecutionPlan compile(TurnRoutingPlan plan) {
        List<ControlExecutionNode> nodes = plan.graph().nodes().stream()
                .map(this::compileNode)
                .toList();
        List<ControlExecutionEdge> edges = plan.graph().edges().stream()
                .map(this::compileEdge)
                .toList();
        return new ControlExecutionPlan(
                nodes,
                edges,
                plan.auditSummary());
    }

    /**
     * Compiles one graph node.
     */
    private ControlExecutionNode compileNode(TurnIntentNode node) {
        return new ControlExecutionNode(
                node.nodeId(),
                node.nodeKind(),
                node.taskType(),
                node.action());
    }

    /**
     * Compiles one graph dependency edge.
     */
    private ControlExecutionEdge compileEdge(TurnIntentEdge edge) {
        return new ControlExecutionEdge(
                edge.fromNodeId(),
                edge.toNodeId(),
                compileRelation(edge.relationType()),
                edge.reason());
    }

    /**
     * Maps semantic dependency types to executable dependency types.
     */
    private ControlExecutionRelationType compileRelation(
            TurnDependencyType relationType) {
        return switch (relationType) {
            case DEPENDS_ON_RESULT -> ControlExecutionRelationType
                    .DEPENDS_ON_RESULT;
            case MUST_RUN_AFTER -> ControlExecutionRelationType.MUST_RUN_AFTER;
            case CONFLICTS_WITH, NEEDS_DISAMBIGUATION ->
                    ControlExecutionRelationType.NEEDS_DISAMBIGUATION;
            case ANSWERS_PENDING, INDEPENDENT ->
                    ControlExecutionRelationType.INDEPENDENT;
        };
    }
}
