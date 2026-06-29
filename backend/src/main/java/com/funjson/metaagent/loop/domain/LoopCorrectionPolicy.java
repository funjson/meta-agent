package com.funjson.metaagent.loop.domain;

/**
 * 长任务执行过程中的确定性纠偏策略。
 *
 * <p>它不替代 LoopCompletionPolicy。CompletionPolicy 判断“是否完成”，
 * CorrectionPolicy 判断“下一步动作是否可能让任务漂移、重复或失控”。v0.1
 * 先覆盖最明确的一类问题：工具 Observation 已经返回后，下一轮禁止继续暴露
 * 工具，强制让模型基于已有 Observation 合成结果，避免工具调用循环。</p>
 */
public class LoopCorrectionPolicy {

    /**
     * 判断当前轮次是否应该向模型暴露原生工具。
     *
     * @param context Loop 执行上下文
     * @return true 表示可以暴露工具；false 表示应只允许模型合成
     */
    public boolean allowNativeTools(RunExecutionContext context) {
        return !hasToolObservation(context.feedback());
    }

    /**
     * 对 fallback planner 的计划做确定性纠偏。
     *
     * @param context Loop 执行上下文
     * @param plan 模型或 fallback planner 产生的计划
     * @return 纠偏后的计划
     */
    public LoopPlan correctPlan(
            RunExecutionContext context,
            LoopPlan plan) {
        if (!hasToolObservation(context.feedback())
                || !isToolAction(plan.actionType())) {
            return plan;
        }
        // 这里收敛到 MODEL_CALL，不把“再搜一次”留给模型自由发挥。
        return LoopPlan.modelCall(
                "基于已有工具 Observation 生成面向用户的最终结果",
                "纠偏：已有工具 Observation，禁止重复工具调用并进入结果合成",
                512);
    }

    /**
     * 判断反馈中是否已有工具 Observation。
     */
    private boolean hasToolObservation(String feedback) {
        return feedback != null
                && feedback.contains("上一轮工具动作")
                && feedback.contains("返回");
    }

    /**
     * @return 是否为工具类动作
     */
    private boolean isToolAction(LoopActionType actionType) {
        return actionType == LoopActionType.TOOL_CALL
                || actionType == LoopActionType.RAG_QUERY
                || actionType == LoopActionType.WEB_SEARCH
                || actionType == LoopActionType.FILE_SEARCH
                || actionType == LoopActionType.SKILL_LOAD;
    }
}
