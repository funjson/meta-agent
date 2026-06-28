package com.funjson.metaagent.conversation.application;

import java.util.UUID;

import com.funjson.metaagent.conversation.application.port.out.ConversationStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在独立事务边界中幂等写入 Assistant 消息。
 */
@Service
public class AssistantMessageService {

    private final ConversationStore conversationStore;

    /**
     * 创建 Assistant 消息服务。
     *
     * @param conversationStore Conversation Store Port
     */
    public AssistantMessageService(ConversationStore conversationStore) {
        this.conversationStore = conversationStore;
    }

    /**
     * 幂等写入一条与用户消息关联的 Assistant 回复。
     *
     * @param conversationId Conversation ID
     * @param userMessageId 来源用户消息
     * @param jobId Job ID
     * @param taskRunId TaskRun ID
     * @param content 回复内容
     * @param messageType 消息类型
     */
    @Transactional
    public void append(
            UUID conversationId,
            UUID userMessageId,
            UUID jobId,
            UUID taskRunId,
            String content,
            String messageType) {
        String key = "assistant:" + userMessageId + ":" + messageType;
        if (conversationStore.findMessageByIdempotencyKey(key).isPresent()) {
            return;
        }
        conversationStore.insertMessage(
                UUID.randomUUID(),
                conversationId,
                "ASSISTANT",
                messageType,
                content == null ? "任务已执行完成。" : content,
                key,
                jobId,
                taskRunId);
    }
}
