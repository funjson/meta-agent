package com.funjson.metaagent.loop.domain;

/**
 * Loop 完成验收模型的稳定返回合同。
 *
 * @param decision 语义判定
 * @param confidence 判定置信度，取值范围 0..1
 * @param summary 可审计摘要
 * @param feedback 如果未完成，传给下一轮 LoopNode 的调整反馈
 */
public record LoopCompletionJudgment(
        LoopCompletionJudgmentDecision decision,
        double confidence,
        String summary,
        String feedback) {

    /** 默认置信度门槛；低置信完成不会直接放行。 */
    private static final double MIN_COMPLETE_CONFIDENCE = 0.55D;

    /**
     * 规整 Judge 输出，避免应用层解析异常污染领域策略。
     */
    public LoopCompletionJudgment {
        decision = decision == null
                ? LoopCompletionJudgmentDecision.UNKNOWN
                : decision;
        confidence = Math.max(0D, Math.min(1D, confidence));
        summary = summary == null ? "" : summary.trim();
        feedback = feedback == null ? "" : feedback.trim();
    }

    /**
     * @return 是否可以被 LoopEvaluator 直接作为完成判定使用
     */
    public boolean confidentComplete() {
        return decision == LoopCompletionJudgmentDecision.COMPLETE
                && confidence >= MIN_COMPLETE_CONFIDENCE;
    }

    /**
     * 创建完成判定。
     *
     * @param summary 完成摘要
     * @return 完成判定
     */
    public static LoopCompletionJudgment complete(String summary) {
        return new LoopCompletionJudgment(
                LoopCompletionJudgmentDecision.COMPLETE,
                1D,
                summary,
                "");
    }

    /**
     * 创建需要继续动作的判定。
     *
     * @param summary 审计摘要
     * @param feedback 下一轮反馈
     * @return 未完成判定
     */
    public static LoopCompletionJudgment needMoreAction(
            String summary,
            String feedback) {
        return new LoopCompletionJudgment(
                LoopCompletionJudgmentDecision.NEED_MORE_ACTION,
                1D,
                summary,
                feedback);
    }

    /**
     * 创建不可用判定，交给规则兜底。
     *
     * @param summary 不可用原因
     * @return UNKNOWN 判定
     */
    public static LoopCompletionJudgment unknown(String summary) {
        return new LoopCompletionJudgment(
                LoopCompletionJudgmentDecision.UNKNOWN,
                0D,
                summary,
                "");
    }
}
