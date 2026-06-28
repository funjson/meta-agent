package com.funjson.metaagent.conversation.application;

import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.conversation.api.CreateConversationRequest;
import com.funjson.metaagent.conversation.application.port.out.ConversationStore;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提供 Conversation 创建和查询用例。
 */
@Service
public class ConversationService {

    private final ConversationStore conversationStore;

    /**
     * 创建 Conversation Service。
     *
     * @param conversationStore Conversation Store Port
     */
    public ConversationService(ConversationStore conversationStore) {
        this.conversationStore = conversationStore;
    }

    /**
     * 创建 Conversation，并应用默认 AgentProfile 与 Provider。
     *
     * @param request 创建请求
     * @return Conversation
     */
    @Transactional
    public ConversationView create(CreateConversationRequest request) {
        UUID id = UUID.randomUUID();
        String profileId = request.agentProfileId() == null || request.agentProfileId().isBlank()
                ? "general-agent"
                : request.agentProfileId().trim();
        String providerId = request.providerId() == null || request.providerId().isBlank()
                ? "auto"
                : request.providerId().trim();
        if (!conversationStore.existsActiveAgentProfile(profileId)) {
            throw new RuntimeStateException(
                    "AGENT_PROFILE_NOT_FOUND",
                    "Active AgentProfile not found: " + profileId);
        }
        conversationStore.insertConversation(id, profileId, providerId);
        return get(id);
    }

    /**
     * 查询 Conversation 及其消息。
     *
     * @param id Conversation ID
     * @return Conversation
     */
    @Transactional(readOnly = true)
    public ConversationView get(UUID id) {
        ConversationView conversation = conversationStore.findConversation(id)
                .orElseThrow(() -> new RuntimeStateException(
                        "CONVERSATION_NOT_FOUND",
                        "Conversation not found: " + id));
        return conversation.withMessages(conversationStore.findMessages(id));
    }

    /**
     * 查询当前单用户工作区中的 Conversation 列表。
     *
     * <p>列表项不携带消息正文，避免侧边栏轮询时重复拉取大块上下文。</p>
     *
     * @return Conversation 摘要列表
     */
    @Transactional(readOnly = true)
    public List<ConversationView> list() {
        return conversationStore.findConversations();
    }
}
