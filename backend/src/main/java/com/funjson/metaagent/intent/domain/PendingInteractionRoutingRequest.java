package com.funjson.metaagent.intent.domain;

import java.util.List;

/**
 * Pending Interaction Router 的结构化输入。
 *
 * @param userMessage 当前用户消息
 * @param conversationContext Conversation 级上下文摘要
 * @param candidates 当前打开的等待交互候选
 * @param modelRoutingAllowed 是否允许调用模型路由
 */
public record PendingInteractionRoutingRequest(
        String userMessage,
        String conversationContext,
        List<PendingInteractionCandidate> candidates,
        boolean modelRoutingAllowed) {

    /** 复制集合并归一化文本。 */
    public PendingInteractionRoutingRequest {
        userMessage = userMessage == null ? "" : userMessage.trim();
        conversationContext = conversationContext == null
                ? ""
                : conversationContext.trim();
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
