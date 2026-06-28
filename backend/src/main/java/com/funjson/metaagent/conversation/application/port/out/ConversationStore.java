package com.funjson.metaagent.conversation.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.conversation.api.MessageView;

/**
 * 定义 Conversation 与 Message 的持久化端口。
 */
public interface ConversationStore {

    /** @return AgentProfile 是否存在且可用 */
    boolean existsActiveAgentProfile(String agentProfileId);

    /** 插入 Conversation。 */
    void insertConversation(
            UUID id,
            String agentProfileId,
            String providerId);

    /** @return Conversation */
    Optional<ConversationView> findConversation(UUID id);

    /** @return 按更新时间倒序排列的 Conversation 列表 */
    List<ConversationView> findConversations();

    /** @return Conversation 下可展示、可进入上下文工程的消息 */
    List<MessageView> findMessages(UUID conversationId);

    /** @return 幂等消息 */
    Optional<MessageView> findMessageByIdempotencyKey(
            String idempotencyKey);

    /** @return 指定 Message */
    Optional<MessageView> findMessageById(UUID messageId);

    /** 插入消息。 */
    void insertMessage(
            UUID id,
            UUID conversationId,
            String role,
            String messageType,
            String content,
            String idempotencyKey,
            UUID jobId,
            UUID taskRunId);

    /** 关联消息与 Job。 */
    void linkMessageToJob(UUID messageId, UUID jobId);

    /** 更新 Conversation 活跃 Job 和标题。 */
    void activateJobAndRetitle(
            UUID conversationId,
            UUID jobId,
            String title);
}
