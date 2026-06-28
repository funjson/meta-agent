package com.funjson.metaagent.conversation.infrastructure.persistence.mybatis;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.conversation.api.MessageView;
import com.funjson.metaagent.conversation.application.port.out.ConversationStore;
import org.springframework.stereotype.Repository;

/**
 * 适配 Conversation Application 与 MyBatis 持久化 Mapper。
 */
@Repository
public class ConversationRepository implements ConversationStore {

    private final ConversationPersistenceMapper mapper;

    /**
     * 创建 Conversation Repository。
     *
     * @param mapper MyBatis Mapper
     */
    public ConversationRepository(ConversationPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 判断 AgentProfile 是否存在且可用。
     *
     * @param agentProfileId AgentProfile ID
     * @return 是否可用
     */
    public boolean existsActiveAgentProfile(String agentProfileId) {
        return mapper.countActiveAgentProfile(agentProfileId) > 0;
    }

    /**
     * 插入 Conversation。
     *
     * @param id Conversation ID
     * @param agentProfileId AgentProfile ID
     * @param providerId 默认 Provider
     */
    public void insertConversation(
            UUID id,
            String agentProfileId,
            String providerId) {
        mapper.insertConversation(id, agentProfileId, providerId);
    }

    /**
     * 查询 Conversation。
     *
     * @param id Conversation ID
     * @return Conversation
     */
    public Optional<ConversationView> findConversation(UUID id) {
        return Optional.ofNullable(mapper.findConversation(id))
                .map(this::toConversationView);
    }

    /**
     * 查询 Conversation 列表。
     *
     * @return Conversation 列表
     */
    public List<ConversationView> findConversations() {
        return mapper.findConversations().stream()
                .map(this::toConversationView)
                .toList();
    }

    /**
     * 查询 Conversation 的可见消息。
     *
     * @param conversationId Conversation ID
     * @return 消息列表
     */
    public List<MessageView> findMessages(UUID conversationId) {
        return mapper.findMessages(conversationId).stream()
                .map(this::toMessageView)
                .toList();
    }

    /**
     * 按幂等键查询消息。
     *
     * @param idempotencyKey 幂等键
     * @return 消息
     */
    public Optional<MessageView> findMessageByIdempotencyKey(
            String idempotencyKey) {
        return Optional.ofNullable(
                        mapper.findMessageByIdempotencyKey(idempotencyKey))
                .map(this::toMessageView);
    }

    /**
     * 按 ID 查询消息。
     *
     * @param messageId Message ID
     * @return 消息
     */
    public Optional<MessageView> findMessageById(UUID messageId) {
        return Optional.ofNullable(mapper.findMessageById(messageId))
                .map(this::toMessageView);
    }

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
     */
    public void insertMessage(
            UUID id,
            UUID conversationId,
            String role,
            String messageType,
            String content,
            String idempotencyKey,
            UUID jobId,
            UUID taskRunId) {
        mapper.insertMessage(
                id,
                conversationId,
                role,
                messageType,
                content,
                idempotencyKey,
                jobId,
                taskRunId);
    }

    /**
     * 关联消息和 Job。
     *
     * @param messageId 消息 ID
     * @param jobId Job ID
     */
    public void linkMessageToJob(UUID messageId, UUID jobId) {
        mapper.linkMessageToJob(messageId, jobId);
    }

    /**
     * 更新活跃 Job 和对话标题。
     *
     * @param conversationId Conversation ID
     * @param jobId Job ID
     * @param title 标题
     */
    public void activateJobAndRetitle(
            UUID conversationId,
            UUID jobId,
            String title) {
        mapper.activateJobAndRetitle(conversationId, jobId, title);
    }

    /**
     * 转换 Conversation 数据库行。
     *
     * @param row 数据库行
     * @return ConversationView
     */
    private ConversationView toConversationView(Map<String, Object> row) {
        return new ConversationView(
                UUID.fromString(text(row, "id")),
                text(row, "agentProfileId"),
                text(row, "title"),
                text(row, "status"),
                text(row, "defaultProviderId"),
                uuid(row.get("activeJobId")),
                number(row, "version").longValue(),
                instant(row.get("createdAt")),
                instant(row.get("updatedAt")),
                List.of());
    }

    /**
     * 转换 Message 数据库行。
     *
     * @param row 数据库行
     * @return MessageView
     */
    private MessageView toMessageView(Map<String, Object> row) {
        return new MessageView(
                UUID.fromString(text(row, "id")),
                text(row, "role"),
                text(row, "messageType"),
                text(row, "content"),
                uuid(row.get("jobId")),
                uuid(row.get("taskRunId")),
                instant(row.get("createdAt")));
    }

    /**
     * 读取必填字符串。
     *
     * @param row 数据库行
     * @param key 列名
     * @return 字符串
     */
    private String text(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }

    /**
     * 转换 UUID 字符串。
     *
     * @param value 列值
     * @return UUID 或空
     */
    private UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(String.valueOf(value));
    }

    /**
     * 读取数值。
     *
     * @param row 数据库行
     * @param key 列名
     * @return 数值
     */
    private Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    /**
     * 转换数据库时间。
     *
     * @param value 时间列
     * @return Instant
     */
    private Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.atZone(java.time.ZoneOffset.UTC).toInstant();
        }
        throw new IllegalArgumentException("Unsupported timestamp value: " + value);
    }
}
