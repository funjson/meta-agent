package com.funjson.metaagent.loop.domain;

/**
 * LoopNode Evaluation 阶段的结构化结果。
 *
 * @param decision 决策
 * @param summary 可审计短摘要
 * @param feedback 派生 Child LoopNode 时继承的调整反馈
 */
public record LoopEvaluation(
        LoopEvaluationDecision decision,
        String summary,
        String feedback) {
}
