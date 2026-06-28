package com.funjson.metaagent.clarification.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户回答被绑定回原执行点后的恢复决议。
 *
 * @param clarificationRequestId 澄清请求 ID
 * @param resumeTargetType 恢复目标类型
 * @param resumeTargetId 恢复目标 ID
 * @param answer 用户回答
 * @param extractedFacts 从用户回答中抽取的结构化事实
 * @param missingFields 抽取后仍然缺失的字段
 * @param answerSummary 面向系统审计的回答摘要
 */
public record ClarificationResolution(
        UUID clarificationRequestId,
        ClarificationSourceType resumeTargetType,
        UUID resumeTargetId,
        String answer,
        Map<String, String> extractedFacts,
        List<String> missingFields,
        String answerSummary) {

    /** 复制集合字段，避免后续外部修改影响审计快照。 */
    public ClarificationResolution {
        answer = answer == null ? "" : answer.trim();
        extractedFacts = extractedFacts == null
                ? Map.of()
                : Map.copyOf(extractedFacts);
        missingFields = missingFields == null
                ? List.of()
                : List.copyOf(missingFields);
        answerSummary = answerSummary == null ? "" : answerSummary.trim();
    }

    /**
     * 兼容旧调用的最小恢复决议。
     *
     * @param clarificationRequestId 澄清请求 ID
     * @param resumeTargetType 恢复目标类型
     * @param resumeTargetId 恢复目标 ID
     * @param answer 用户回答
     */
    public ClarificationResolution(
            UUID clarificationRequestId,
            ClarificationSourceType resumeTargetType,
            UUID resumeTargetId,
            String answer) {
        this(
                clarificationRequestId,
                resumeTargetType,
                resumeTargetId,
                answer,
                Map.of(),
                List.of(),
                "");
    }
}
