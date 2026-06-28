package com.funjson.metaagent.clarification.domain;

import java.util.List;

/**
 * 控制澄清问题是否允许继续询问的领域策略。
 */
public class ClarificationPolicy {

    /**
     * 校验新问题不会突破轮数限制或重复询问。
     *
     * @param existingOpenRequests 当前来源下未完成请求
     * @param draft 待创建草稿
     */
    public void requireAllowed(
            List<ClarificationRequest> existingOpenRequests,
            ClarificationRequestDraft draft) {
        for (ClarificationRequest existing : existingOpenRequests) {
            if (existing.status() != ClarificationStatus.OPEN) {
                continue;
            }
            if (sameQuestion(existing.question(), draft.question())) {
                throw new IllegalArgumentException(
                        "Duplicate clarification question is not allowed");
            }
            if (existing.currentRound() >= existing.maxRounds()) {
                throw new IllegalArgumentException(
                        "Clarification round limit has been reached");
            }
        }
    }

    /**
     * 使用规范化文本判断问题是否重复。
     *
     * @param left 已有问题
     * @param right 新问题
     * @return 是否重复
     */
    private boolean sameQuestion(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    /**
     * 规范化问题文本，避免标点或空白造成重复判断失效。
     *
     * @param value 原始文本
     * @return 规范化文本
     */
    private String normalize(String value) {
        return value == null
                ? ""
                : value.replaceAll("\\s+", "")
                        .replaceAll("[?？。.!！,，]", "")
                        .toLowerCase(java.util.Locale.ROOT);
    }
}
