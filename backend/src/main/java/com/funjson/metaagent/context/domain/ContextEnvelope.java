package com.funjson.metaagent.context.domain;

import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.clarification.domain.ClarificationRequest;

/**
 * Conversation 级上下文事实源。
 *
 * <p>Envelope 保存系统已知事实；具体模型调用只能通过 Assembler 生成 Prompt View，
 * 以便后续压缩、检索和可观测追踪统一接入。</p>
 *
 * @param conversation Conversation 元信息
 * @param visibleMessages 可进入用户上下文的消息
 * @param conversationFacts Conversation 级结构化事实
 * @param openClarifications 当前打开的澄清候选
 * @param resolvedClarifications 最近已经解决的澄清事实
 */
public record ContextEnvelope(
        ContextConversation conversation,
        List<ContextMessage> visibleMessages,
        List<ContextFact> conversationFacts,
        List<ClarificationRequest> openClarifications,
        List<ClarificationRequest> resolvedClarifications) {

    /** 复制集合字段，保证上下文快照不可变。 */
    public ContextEnvelope {
        visibleMessages = visibleMessages == null
                ? List.of()
                : List.copyOf(visibleMessages);
        conversationFacts = conversationFacts == null
                ? List.of()
                : List.copyOf(conversationFacts);
        openClarifications = openClarifications == null
                ? List.of()
                : List.copyOf(openClarifications);
        resolvedClarifications = resolvedClarifications == null
                ? List.of()
                : List.copyOf(resolvedClarifications);
    }

    /** @return Conversation ID */
    public UUID conversationId() {
        return conversation.id();
    }
}
