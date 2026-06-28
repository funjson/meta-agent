package com.funjson.metaagent.loop.domain;

/**
 * Loop 层只能验收当前 LoopNode 的局部目标、动作结果和预算。
 */
public interface LoopCompletionPolicy {

    /**
     * 评估当前动作是否完成局部目标。
     *
     * @param context LoopNode 上下文
     * @param actionResult 动作结果
     * @param policy Loop 执行预算
     * @param currentNodeCount 当前节点数
     * @return 局部验收结果
     */
    LoopEvaluation evaluate(
            RunExecutionContext context,
            LoopActionResult actionResult,
            LoopExecutionPolicy policy,
            int currentNodeCount);
}
