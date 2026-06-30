package com.funjson.metaagent.observability.infrastructure.persistence.mybatis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.funjson.metaagent.observability.api.AgentPathNode;
import com.funjson.metaagent.observability.api.AgentPathView;
import com.funjson.metaagent.observability.application.port.out.AgentPathQuery;
import org.springframework.stereotype.Repository;

/**
 * 组装 Control Path 与 Execution Path 的统一投影。
 */
@Repository
public class AgentPathRepository implements AgentPathQuery {

    private final AgentPathMapper mapper;

    /**
     * 创建 Agent Path Repository。
     *
     * @param mapper Agent Path 查询 Mapper
     */
    public AgentPathRepository(AgentPathMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询并按执行树顺序排列 Conversation 的 Agent Path。
     *
     * @param conversationId Conversation ID
     * @return Agent Path
     */
    public AgentPathView find(UUID conversationId) {
        List<AgentPathNode> nodes = new ArrayList<>();
        addIfPresent(nodes, mapper.findConversationNode(conversationId));
        nodes.addAll(mapper.findMessageNodes(conversationId));
        nodes.addAll(mapper.findControlTurnNodes(conversationId));
        nodes.addAll(mapper.findControlDecisionNodes(conversationId));
        nodes.addAll(mapper.findJobNodes(conversationId));
        nodes.addAll(mapper.findTaskNodes(conversationId));
        nodes.addAll(mapper.findClarificationNodes(conversationId));
        nodes.addAll(mapper.findTaskRunNodes(conversationId));
        nodes.addAll(mapper.findLoopRunNodes(conversationId));
        nodes.addAll(mapper.findLoopNodeNodes(conversationId));
        nodes.addAll(mapper.findLoopPhaseNodes(conversationId));
        nodes.addAll(mapper.findCapabilityLoadNodes(conversationId));
        nodes.addAll(mapper.findModelCallNodes(conversationId));
        nodes.addAll(mapper.findToolInvocationNodes(conversationId));
        nodes.addAll(mapper.findWebSearchRunNodes(conversationId));
        nodes.addAll(mapper.findWebSearchCandidateNodes(conversationId));
        nodes.addAll(mapper.findWebSourceDocumentNodes(conversationId));
        nodes.addAll(mapper.findWebEvidenceItemNodes(conversationId));
        nodes.addAll(mapper.findCheckpointNodes(conversationId));
        nodes.addAll(mapper.findEvidenceNodes(conversationId));
        nodes.addAll(mapper.findRecoveryAttemptNodes(conversationId));
        nodes.addAll(mapper.findJobCompletionNodes(conversationId));

        String rootId = "conversation:" + conversationId;
        return new AgentPathView(
                conversationId,
                orderByExecutionTree(nodes, rootId));
    }

    /**
     * 仅在节点存在时加入结果。
     *
     * @param nodes 目标列表
     * @param node 待加入节点
     */
    private void addIfPresent(
            List<AgentPathNode> nodes,
            AgentPathNode node) {
        if (node != null) {
            nodes.add(node);
        }
    }

    /**
     * 根据 parentId 生成稳定的深度优先执行顺序。
     *
     * @param nodes 未排序节点
     * @param rootId 根节点 ID
     * @return 不可变有序节点
     */
    private List<AgentPathNode> orderByExecutionTree(
            List<AgentPathNode> nodes,
            String rootId) {
        Map<String, List<AgentPathNode>> children = new HashMap<>();
        for (AgentPathNode node : nodes) {
            if (node.parentId() != null) {
                children.computeIfAbsent(node.parentId(), ignored -> new ArrayList<>())
                        .add(node);
            }
        }
        Comparator<AgentPathNode> childOrder = Comparator
                .comparing(
                        AgentPathNode::occurredAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(node -> typeRank(node.nodeType()));
        children.values().forEach(items -> items.sort(childOrder));

        List<AgentPathNode> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        nodes.stream()
                .filter(node -> node.id().equals(rootId))
                .findFirst()
                .ifPresent(node -> appendDepthFirst(
                        node,
                        children,
                        visited,
                        ordered));
        nodes.stream()
                .filter(node -> !visited.contains(node.id()))
                .sorted(childOrder)
                .forEach(node -> appendDepthFirst(
                        node,
                        children,
                        visited,
                        ordered));
        return List.copyOf(ordered);
    }

    /**
     * 深度优先追加节点，visited 同时防御异常循环引用。
     *
     * @param node 当前节点
     * @param children 子节点索引
     * @param visited 已访问节点
     * @param ordered 输出列表
     */
    private void appendDepthFirst(
            AgentPathNode node,
            Map<String, List<AgentPathNode>> children,
            Set<String> visited,
            List<AgentPathNode> ordered) {
        if (!visited.add(node.id())) {
            return;
        }
        ordered.add(node);
        for (AgentPathNode child : children.getOrDefault(node.id(), List.of())) {
            appendDepthFirst(child, children, visited, ordered);
        }
    }

    /**
     * 在同一时间戳下按架构阶段稳定排序。
     *
     * @param nodeType 节点类型
     * @return 排序权重
     */
    private int typeRank(String nodeType) {
        return switch (nodeType) {
            case "MESSAGE" -> 0;
            case "CONTROL_DECISION" -> 1;
            case "JOB" -> 2;
            case "TASK" -> 3;
            case "CLARIFICATION_REQUEST" -> 4;
            case "TASK_RUN" -> 5;
            case "LOOP_RUN" -> 6;
            case "LOOP_NODE" -> 7;
            case "LOOP_PHASE" -> 8;
            case "CAPABILITY_LOAD" -> 9;
            case "MODEL_CALL", "TOOL_CALL" -> 10;
            case "WEB_SEARCH_RUN" -> 11;
            case "WEB_SEARCH_CANDIDATE" -> 12;
            case "WEB_SOURCE" -> 13;
            case "WEB_EVIDENCE" -> 14;
            case "CHECKPOINT" -> 15;
            case "EVIDENCE" -> 16;
            case "RECOVERY_ATTEMPT" -> 17;
            case "JOB_COMPLETION" -> 18;
            default -> 20;
        };
    }
}
