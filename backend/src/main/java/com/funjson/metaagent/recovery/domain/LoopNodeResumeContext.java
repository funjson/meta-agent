package com.funjson.metaagent.recovery.domain;

import com.funjson.metaagent.loop.domain.RunExecutionContext;

/**
 * 恢复等待子执行的 origin LoopNode 所需的持久化计划摘要。
 *
 * @param context origin LoopNode 上下文
 * @param completionCriterion 原 Planning 完成判据
 */
public record LoopNodeResumeContext(
        RunExecutionContext context,
        String completionCriterion) {
}
