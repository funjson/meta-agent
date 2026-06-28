package com.funjson.metaagent.intent.domain;

import java.util.UUID;

/**
 * Pending Interaction Matcher 的可审计结果。
 *
 * @param matchType 匹配类型
 * @param targetId 命中的候选 ID
 * @param confidence 置信度
 * @param summary 决策摘要
 */
public record PendingInteractionMatch(
        PendingInteractionMatchType matchType,
        UUID targetId,
        double confidence,
        String summary) {

    /** @return 是否命中一个等待澄清请求 */
    public boolean answeredClarification() {
        return matchType == PendingInteractionMatchType.ANSWER_CLARIFICATION
                && targetId != null;
    }
}
