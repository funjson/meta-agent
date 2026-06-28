package com.funjson.metaagent.context.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.clarification.application.ClarificationService;
import com.funjson.metaagent.clarification.domain.ClarificationRequest;
import com.funjson.metaagent.context.domain.ContextBlock;
import com.funjson.metaagent.context.domain.ContextBlockType;
import com.funjson.metaagent.context.domain.ContextConversation;
import com.funjson.metaagent.context.domain.ContextEnvelope;
import com.funjson.metaagent.context.domain.ContextMessage;
import com.funjson.metaagent.conversation.application.port.out.ConversationStore;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.stereotype.Service;

/**
 * 统一装配 Conversation、Intent、Loop 所需的上下文视图。
 *
 * <p>当前版本不做最近 N 轮截断和压缩；后续的压缩器会在 Envelope 构建阶段接入，
 * 而不是散落在 Intent 或 Loop 内部。</p>
 */
@Service
public class ContextAssembler {

    private static final int DEFAULT_TOKEN_BUDGET = 4096;
    private static final int RESOLVED_CLARIFICATION_CONTEXT_LIMIT = 20;

    private final ConversationStore conversationStore;
    private final ClarificationService clarificationService;

    /**
     * 创建上下文装配器。
     *
     * @param conversationStore Conversation Store
     * @param clarificationService Clarification Service
     */
    public ContextAssembler(
            ConversationStore conversationStore,
            ClarificationService clarificationService) {
        this.conversationStore = conversationStore;
        this.clarificationService = clarificationService;
    }

    /**
     * 构造 Conversation 级上下文事实源。
     *
     * @param conversationId Conversation ID
     * @return 上下文 Envelope
     */
    public ContextEnvelope envelope(UUID conversationId) {
        var conversation = conversationStore.findConversation(conversationId)
                .orElseThrow(() -> new RuntimeStateException(
                        "CONVERSATION_NOT_FOUND",
                        "Conversation not found: " + conversationId));
        return new ContextEnvelope(
                new ContextConversation(
                        conversation.id(),
                        conversation.title(),
                        conversation.activeJobId()),
                conversationStore.findMessages(conversationId).stream()
                        .map(message -> new ContextMessage(
                                message.role(),
                                message.messageType(),
                                message.content()))
                        .toList(),
                clarificationService.findOpenByConversation(conversationId),
                clarificationService.findRecentResolvedByConversation(
                        conversationId,
                        RESOLVED_CLARIFICATION_CONTEXT_LIMIT));
    }

    /**
     * 渲染给 Intent 分类器的上下文摘要。
     *
     * @param envelope 上下文事实源
     * @return Intent Prompt View
     */
    public String intentPromptView(ContextEnvelope envelope) {
        List<String> sections = new ArrayList<>();
        sections.add("Conversation: %s, title=%s, activeJob=%s".formatted(
                envelope.conversationId(),
                envelope.conversation().title(),
                envelope.conversation().activeJobId()));
        sections.add("可见消息：\n" + renderMessages(envelope.visibleMessages()));
        sections.add("等待交互候选：\n"
                + renderClarifications(envelope.openClarifications()));
        sections.add("已解决澄清事实（系统可用，不直接展示给用户）：\n"
                + renderResolvedClarifications(
                        envelope.resolvedClarifications()));
        return String.join("\n\n", sections);
    }

    /**
     * 生成可进入 Loop Prompt 的通用上下文块。
     *
     * @param envelope Conversation 事实源
     * @return 上下文块
     */
    public List<ContextBlock> loopConversationBlocks(
            ContextEnvelope envelope) {
        List<ContextBlock> blocks = new ArrayList<>();
        blocks.add(block(
                ContextBlockType.CONVERSATION,
                "Visible Conversation Messages",
                renderMessages(envelope.visibleMessages())));
        blocks.add(block(
                ContextBlockType.PENDING_INTERACTION,
                "Open Waiting Interactions",
                renderClarifications(envelope.openClarifications())));
        blocks.add(block(
                ContextBlockType.MEMORY,
                "Resolved Clarification Facts",
                renderResolvedClarifications(
                        envelope.resolvedClarifications())));
        return List.copyOf(blocks);
    }

    /**
     * 创建上下文块并估算 Token。
     *
     * @param type 类型
     * @param title 标题
     * @param content 内容
     * @return 上下文块
     */
    public ContextBlock block(
            ContextBlockType type,
            String title,
            String content) {
        return new ContextBlock(
                type,
                title,
                content,
                estimateTokens(content));
    }

    /** @return 默认 Loop Prompt 预算。 */
    public int defaultTokenBudget() {
        return DEFAULT_TOKEN_BUDGET;
    }

    /**
     * 渲染可见消息。
     */
    private String renderMessages(List<ContextMessage> messages) {
        if (messages.isEmpty()) {
            return "无可见历史消息";
        }
        return messages.stream()
                .map(message -> "- [%s/%s] %s".formatted(
                        message.role(),
                        message.messageType(),
                        oneLine(message.content())))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("无可见历史消息");
    }

    /**
     * 渲染等待澄清候选。
     */
    private String renderClarifications(
            List<ClarificationRequest> clarifications) {
        if (clarifications.isEmpty()) {
            return "无打开的等待项";
        }
        return clarifications.stream()
                .map(request -> "- id=%s job=%s source=%s reason=%s question=%s".formatted(
                        request.id(),
                        request.jobId(),
                        request.sourceType(),
                        request.reasonType(),
                        oneLine(request.question())))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("无打开的等待项");
    }

    /**
     * 渲染已经解决的澄清事实。
     *
     * <p>这里可以包含系统 JSON 摘要，因为它只进入模型上下文，不会作为聊天消息展示给用户。</p>
     */
    private String renderResolvedClarifications(
            List<ClarificationRequest> clarifications) {
        if (clarifications.isEmpty()) {
            return "无已解决澄清事实";
        }
        return clarifications.stream()
                .map(request -> "- id=%s job=%s source=%s answer=%s resolution=%s"
                        .formatted(
                                request.id(),
                                request.jobId(),
                                request.sourceType(),
                                oneLine(request.answer()),
                                oneLine(request.resolutionJson())))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("无已解决澄清事实");
    }

    /**
     * 清理单行摘要，避免 Prompt 被大块换行冲散。
     */
    private String oneLine(String value) {
        String normalized = value == null
                ? ""
                : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500
                ? normalized
                : normalized.substring(0, 497) + "...";
    }

    /**
     * 简单估算 Token，后续替换为 Provider tokenizer 或压缩器输出。
     */
    private int estimateTokens(String content) {
        return Math.max(1, content == null ? 0 : content.length() / 3);
    }
}
