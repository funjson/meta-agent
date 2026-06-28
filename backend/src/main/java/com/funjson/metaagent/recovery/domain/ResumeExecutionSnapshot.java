package com.funjson.metaagent.recovery.domain;

import java.util.UUID;

import com.funjson.metaagent.loop.domain.LoopActionType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;

/**
 * ResumeExecutor 从事实表重建的稳定执行游标。
 *
 * @param checkpointId Checkpoint ID
 * @param checkpointType Checkpoint 类型
 * @param context LoopNode 执行上下文
 * @param actionType 已规划动作类型
 * @param completionCriterion 已持久化完成判据
 * @param maxTokens 模型预算
 * @param actionPhaseId ACTION_EXECUTION Phase ID
 * @param actionPhaseStatus Phase 状态
 */
public record ResumeExecutionSnapshot(
        UUID checkpointId,
        String checkpointType,
        RunExecutionContext context,
        LoopActionType actionType,
        String completionCriterion,
        int maxTokens,
        UUID actionPhaseId,
        String actionPhaseStatus) {
}
