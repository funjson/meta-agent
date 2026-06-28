package com.funjson.metaagent.conversation.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 定义 Conversation 与 Message 的 MyBatis 映射。
 */
@Mapper
public interface ConversationPersistenceMapper {

    /**
     * 判断 AgentProfile 是否存在且可用。
     *
     * @param agentProfileId AgentProfile ID
     * @return 匹配数量
     */
    int countActiveAgentProfile(
            @Param("agentProfileId") String agentProfileId);

    /**
     * 插入 Conversation。
     *
     * @param id Conversation ID
     * @param agentProfileId AgentProfile ID
     * @param providerId 默认 Provider
     * @return 插入行数
     */
    int insertConversation(
            @Param("id") UUID id,
            @Param("agentProfileId") String agentProfileId,
            @Param("providerId") String providerId);

    /**
     * 查询 Conversation 行。
     *
     * @param id Conversation ID
     * @return 数据库行
     */
    Map<String, Object> findConversation(@Param("id") UUID id);

    /**
     * 查询 Conversation 列表。
     *
     * @return Conversation 行
     */
    List<Map<String, Object>> findConversations();

    /**
     * 查询 Conversation 的可见消息。
     *
     * @param conversationId Conversation ID
     * @return 消息行
     */
    List<Map<String, Object>> findMessages(
            @Param("conversationId") UUID conversationId);

    /**
     * 按幂等键查询消息。
     *
     * @param idempotencyKey 幂等键
     * @return 消息行
     */
    Map<String, Object> findMessageByIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey);

    /**
     * 按 ID 查询消息。
     *
     * @param messageId Message ID
     * @return 消息行
     */
    Map<String, Object> findMessageById(
            @Param("messageId") UUID messageId);

    /**
     * 插入消息。
     *
     * @param id 消息 ID
     * @param conversationId Conversation ID
     * @param role 角色
     * @param messageType 消息类型
     * @param content 内容
     * @param idempotencyKey 幂等键
     * @param jobId Job ID
     * @param taskRunId TaskRun ID
     * @return 插入行数
     */
    int insertMessage(
            @Param("id") UUID id,
            @Param("conversationId") UUID conversationId,
            @Param("role") String role,
            @Param("messageType") String messageType,
            @Param("content") String content,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("jobId") UUID jobId,
            @Param("taskRunId") UUID taskRunId);

    /**
     * 关联消息和 Job。
     *
     * @param messageId 消息 ID
     * @param jobId Job ID
     * @return 更新行数
     */
    int linkMessageToJob(
            @Param("messageId") UUID messageId,
            @Param("jobId") UUID jobId);

    /**
     * 更新 Conversation 的活跃 Job 与标题。
     *
     * @param conversationId Conversation ID
     * @param jobId Job ID
     * @param title 新标题
     * @return 更新行数
     */
    int activateJobAndRetitle(
            @Param("conversationId") UUID conversationId,
            @Param("jobId") UUID jobId,
            @Param("title") String title);
}
