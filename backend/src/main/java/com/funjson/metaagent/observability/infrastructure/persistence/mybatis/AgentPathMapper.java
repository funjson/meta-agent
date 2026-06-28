package com.funjson.metaagent.observability.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.observability.api.AgentPathNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 查询 Agent Path Projection 的 MyBatis Mapper。
 */
@Mapper
public interface AgentPathMapper {

    /**
     * 查询 Conversation 根节点。
     *
     * @param conversationId Conversation ID
     * @return 根节点
     */
    AgentPathNode findConversationNode(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询消息节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findMessageNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询 ControlTurn 节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findControlTurnNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询 ControlDecision 节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findControlDecisionNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询 Job 节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findJobNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询 Task 节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findTaskNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询 ClarificationRequest 节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findClarificationNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询 TaskRun 节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findTaskRunNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询 LoopRun 节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findLoopRunNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询 LoopNode 节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findLoopNodeNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询 LoopNode 内部阶段节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findLoopPhaseNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询 CapabilityLoad 节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findCapabilityLoadNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询模型调用节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findModelCallNodes(
            @Param("conversationId") UUID conversationId);

    /** 查询 ToolInvocation 节点。 */
    List<AgentPathNode> findToolInvocationNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询 Checkpoint 节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findCheckpointNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询 Evidence 节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findEvidenceNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询 RecoveryAttempt 节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findRecoveryAttemptNodes(
            @Param("conversationId") UUID conversationId);

    /**
     * 查询 Job Completion 节点。
     *
     * @param conversationId Conversation ID
     * @return 节点列表
     */
    List<AgentPathNode> findJobCompletionNodes(
            @Param("conversationId") UUID conversationId);
}
