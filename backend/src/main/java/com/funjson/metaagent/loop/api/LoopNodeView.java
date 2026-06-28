package com.funjson.metaagent.loop.api;

import java.time.Instant;
import java.util.UUID;

import com.funjson.metaagent.loop.domain.LoopNodeStatus;

/**
 * LoopNode 的 API 视图。
 *
 * @param id LoopNode ID
 * @param parentNodeId 父 LoopNode ID
 * @param depth 节点深度
 * @param iterationNo 调整迭代号
 * @param actionType 动作类型
 * @param goal 节点目标
 * @param status 状态
 * @param currentPhase 当前或最终阶段
 * @param observation 观察摘要
 * @param output 输出
 * @param phases 内部阶段记录
 * @param startedAt 开始时间
 * @param completedAt 完成时间
 */
public record LoopNodeView(
        UUID id,
        UUID parentNodeId,
        int depth,
        int iterationNo,
        String actionType,
        String goal,
        LoopNodeStatus status,
        String currentPhase,
        String observation,
        String output,
        java.util.List<LoopPhaseView> phases,
        Instant startedAt,
        Instant completedAt) {
}
