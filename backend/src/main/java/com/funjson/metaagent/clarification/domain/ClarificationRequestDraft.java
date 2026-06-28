package com.funjson.metaagent.clarification.domain;

/**
 * 在规划或执行阶段产生、尚未落库的澄清请求草稿。
 *
 * @param sourceType 澄清来源层
 * @param reasonType 结构化阻塞原因
 * @param blockingSummary 阻塞摘要
 * @param question 面向用户的问题
 * @param maxRounds 最大追问轮数
 * @param contractJson 系统用结构化澄清合同 JSON
 */
public record ClarificationRequestDraft(
        ClarificationSourceType sourceType,
        ClarificationReasonType reasonType,
        String blockingSummary,
        String question,
        int maxRounds,
        String contractJson) {

    /**
     * 兼容旧调用的草稿构造器。
     *
     * @param sourceType 澄清来源层
     * @param reasonType 结构化阻塞原因
     * @param blockingSummary 阻塞摘要
     * @param question 面向用户的问题
     * @param maxRounds 最大追问轮数
     */
    public ClarificationRequestDraft(
            ClarificationSourceType sourceType,
            ClarificationReasonType reasonType,
            String blockingSummary,
            String question,
            int maxRounds) {
        this(
                sourceType,
                reasonType,
                blockingSummary,
                question,
                maxRounds,
                "{}");
    }

    /**
     * 校验草稿的最小可审计字段。
     */
    public ClarificationRequestDraft {
        if (sourceType == null) {
            throw new IllegalArgumentException(
                    "Clarification source type is required");
        }
        if (reasonType == null) {
            throw new IllegalArgumentException(
                    "Clarification reason type is required");
        }
        if (blockingSummary == null || blockingSummary.isBlank()) {
            throw new IllegalArgumentException(
                    "Clarification blocking summary is required");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException(
                    "Clarification question is required");
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException(
                    "Clarification max rounds must be positive");
        }
        contractJson = contractJson == null || contractJson.isBlank()
                ? "{}"
                : contractJson.trim();
    }
}
