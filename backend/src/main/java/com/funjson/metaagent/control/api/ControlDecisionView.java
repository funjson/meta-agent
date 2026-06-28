package com.funjson.metaagent.control.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 对外展示可审计的 Control 意图决策。
 *
 * @param id 决策 ID
 * @param controlTurnId ControlTurn ID
 * @param sourceMessageId 来源消息
 * @param jobId 创建或控制的 Job
 * @param intentType 意图类型
 * @param confidence 识别置信度
 * @param classifier 使用的分类器
 * @param goalSummary 目标摘要
 * @param decisionSummary 决策摘要
 * @param constraints 用户约束
 * @param requiresClarification 是否需要澄清
 * @param compoundTask 是否为复合任务
 * @param riskLevel 风险等级
 * @param createdAt 创建时间
 */
public record ControlDecisionView(
        UUID id,
        UUID controlTurnId,
        UUID sourceMessageId,
        UUID jobId,
        String intentType,
        double confidence,
        String classifier,
        String goalSummary,
        String decisionSummary,
        List<String> constraints,
        boolean requiresClarification,
        boolean compoundTask,
        String riskLevel,
        Instant createdAt) {
}
