package com.funjson.metaagent.clarification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 表示一次可恢复、可审计的人工澄清请求。
 *
 * @param id 澄清请求 ID
 * @param conversationId Conversation ID
 * @param jobId 可选 Job ID
 * @param taskId 可选 Task ID
 * @param taskRunId 可选 TaskRun ID
 * @param loopNodeId 可选 LoopNode ID
 * @param sourceType 来源层
 * @param reasonType 阻塞原因类型
 * @param status 当前状态
 * @param question 当前问题
 * @param contractJson 系统用结构化澄清合同 JSON
 * @param answer 用户回答
 * @param answerMessageId 回答消息 ID
 * @param resolutionJson 恢复决议 JSON
 * @param resolvedAt 解决时间
 * @param blockingSummary 阻塞摘要
 * @param maxRounds 最大追问轮数
 * @param currentRound 当前轮数
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record ClarificationRequest(
        UUID id,
        UUID conversationId,
        UUID jobId,
        UUID taskId,
        UUID taskRunId,
        UUID loopNodeId,
        ClarificationSourceType sourceType,
        ClarificationReasonType reasonType,
        ClarificationStatus status,
        String question,
        String contractJson,
        String answer,
        UUID answerMessageId,
        String resolutionJson,
        Instant resolvedAt,
        String blockingSummary,
        int maxRounds,
        int currentRound,
        Instant createdAt,
        Instant updatedAt) {
}
