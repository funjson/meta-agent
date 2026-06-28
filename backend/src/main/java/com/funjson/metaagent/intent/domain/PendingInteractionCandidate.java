package com.funjson.metaagent.intent.domain;

import java.util.UUID;

/**
 * 用户新消息可能要响应的等待交互候选。
 *
 * <p>Intent 层只看稳定合同，不直接依赖 Clarification 聚合对象，避免把
 * Conversation 路由逻辑塞回 Conversation 或 Job。</p>
 *
 * @param id 候选 ID
 * @param targetType 候选类型
 * @param jobId 关联 Job
 * @param taskId 可选 Task
 * @param taskRunId 可选 TaskRun
 * @param loopNodeId 可选 LoopNode
 * @param question 对用户提出的问题
 * @param blockingSummary 等待原因摘要
 * @param contractJson 系统用等待合同 JSON
 */
public record PendingInteractionCandidate(
        UUID id,
        String targetType,
        UUID jobId,
        UUID taskId,
        UUID taskRunId,
        UUID loopNodeId,
        String question,
        String blockingSummary,
        String contractJson) {

    /**
     * 兼容旧调用的候选构造器。
     */
    public PendingInteractionCandidate(
            UUID id,
            String targetType,
            UUID jobId,
            UUID taskId,
            UUID taskRunId,
            UUID loopNodeId,
            String question,
            String blockingSummary) {
        this(
                id,
                targetType,
                jobId,
                taskId,
                taskRunId,
                loopNodeId,
                question,
                blockingSummary,
                "{}");
    }

    /** 归一化可空文本字段。 */
    public PendingInteractionCandidate {
        question = question == null ? "" : question.trim();
        blockingSummary = blockingSummary == null
                ? ""
                : blockingSummary.trim();
        contractJson = contractJson == null || contractJson.isBlank()
                ? "{}"
                : contractJson.trim();
    }
}
