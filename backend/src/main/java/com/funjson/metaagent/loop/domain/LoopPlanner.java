package com.funjson.metaagent.loop.domain;

import com.funjson.metaagent.capability.domain.CapabilityPlanningContext;

/**
 * 为当前 C3 MODEL_CALL 能力形成稳定、可审计的最小计划。
 */
public class LoopPlanner {

    /**
     * 根据节点上下文生成动作计划。
     *
     * @param context 节点执行上下文
     * @param capabilityContext Skill 作用域与派生请求
     * @return 结构化计划
     */
    public LoopPlan plan(
            RunExecutionContext context,
            CapabilityPlanningContext capabilityContext) {
        ExecutionDerivationRequest derivation =
                capabilityContext.derivationRequest();
        if (derivation != null
                && derivation.type() == ExecutionDerivationType.CHILD_JOB) {
            return LoopPlan.childJob(
                    "Child Job 完成并回传满足合同的结果",
                    derivation.reason(),
                    derivation);
        }
        if (derivation != null
                && derivation.type() == ExecutionDerivationType.CHILD_LOOP) {
            return LoopPlan.childLoop(
                    "Child LoopNode 完成其局部目标",
                    derivation.reason(),
                    derivation);
        }
        String summary = context.feedback().isBlank()
                ? "调用已选择的模型 Provider 完成当前节点目标"
                : "根据父节点评估反馈调整输入后再次调用模型 Provider";
        return LoopPlan.modelCall(
                "Provider 返回非空、可展示的最终结果",
                summary,
                512);
    }
}
