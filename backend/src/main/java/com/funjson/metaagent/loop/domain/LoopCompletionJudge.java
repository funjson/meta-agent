package com.funjson.metaagent.loop.domain;

/**
 * Loop 完成语义验收端口。
 *
 * <p>领域层只依赖这个稳定接口；具体实现可以是模型 Judge、测试桩或规则 Judge。
 * 这样 LoopEvaluator 不需要知道 Provider、PromptRegistry 或外部模型调用细节。</p>
 */
@FunctionalInterface
public interface LoopCompletionJudge {

    /**
     * 判断候选动作结果是否已经满足当前 Loop 的用户可见交付合同。
     *
     * @param context 当前 LoopNode 上下文
     * @param actionResult 候选动作结果
     * @param policy Loop 执行预算
     * @param currentNodeCount 当前 LoopTree 节点数量
     * @return 语义验收判定；实现不可抛出业务异常给领域策略
     */
    LoopCompletionJudgment judge(
            RunExecutionContext context,
            LoopActionResult actionResult,
            LoopExecutionPolicy policy,
            int currentNodeCount);

    /**
     * @return 不做模型判断、始终交给规则兜底的 Judge
     */
    static LoopCompletionJudge noOp() {
        return (context, actionResult, policy, currentNodeCount) ->
                LoopCompletionJudgment.unknown("No completion judge configured");
    }
}
