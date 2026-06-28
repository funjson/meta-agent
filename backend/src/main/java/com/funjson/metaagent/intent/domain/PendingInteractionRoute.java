package com.funjson.metaagent.intent.domain;

import java.util.UUID;

/**
 * Pending Interaction Router 的结构化输出。
 *
 * @param routeType 路由类型
 * @param targetId 命中的等待交互 ID
 * @param confidence 置信度
 * @param answerText 应绑定到目标等待项的用户补充文本
 * @param facts 从补充文本中抽取的结构化事实
 * @param userFacingMessage 必要时展示给用户的澄清/消歧问题
 * @param auditSummary 系统审计摘要
 */
public record PendingInteractionRoute(
        PendingInteractionRouteType routeType,
        UUID targetId,
        double confidence,
        String answerText,
        PendingInteractionFacts facts,
        String userFacingMessage,
        String auditSummary) {

    /** 校验并归一化输出合同。 */
    public PendingInteractionRoute {
        if (routeType == null) {
            throw new IllegalArgumentException(
                    "Pending interaction route type is required");
        }
        confidence = Math.max(0, Math.min(1, confidence));
        answerText = answerText == null ? "" : answerText.trim();
        facts = facts == null ? PendingInteractionFacts.empty() : facts;
        userFacingMessage = userFacingMessage == null
                ? ""
                : userFacingMessage.trim();
        auditSummary = auditSummary == null ? "" : auditSummary.trim();
    }

    /** @return 是否命中可回答的等待项 */
    public boolean targetsWaitingInteraction() {
        return (routeType == PendingInteractionRouteType.ANSWER_CLARIFICATION
                || routeType == PendingInteractionRouteType.SELECT_PENDING_INTERACTION)
                && targetId != null;
    }

    /**
     * 创建新意图路由。
     *
     * @param summary 审计摘要
     * @return 新意图路由
     */
    public static PendingInteractionRoute newIntent(String summary) {
        return new PendingInteractionRoute(
                PendingInteractionRouteType.NEW_INTENT,
                null,
                0.7,
                "",
                PendingInteractionFacts.empty(),
                "",
                summary);
    }
}
