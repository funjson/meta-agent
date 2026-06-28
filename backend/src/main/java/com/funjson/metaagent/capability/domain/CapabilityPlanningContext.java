package com.funjson.metaagent.capability.domain;

import com.funjson.metaagent.loop.domain.ExecutionDerivationRequest;

/**
 * Capability 应用后提交给 Loop Planner 的上下文。
 *
 * @param scopedContext 当前节点可见的规范
 * @param derivationRequest 步骤型或流程型派生请求
 */
public record CapabilityPlanningContext(
        ScopedCapabilityContext scopedContext,
        ExecutionDerivationRequest derivationRequest) {

    /**
     * 返回无 Skill 动作的空上下文。
     *
     * @return 空规划上下文
     */
    public static CapabilityPlanningContext empty() {
        return new CapabilityPlanningContext(
                ScopedCapabilityContext.empty(),
                null);
    }

    /**
     * 校验规划上下文。
     */
    public CapabilityPlanningContext {
        if (scopedContext == null) {
            scopedContext = ScopedCapabilityContext.empty();
        }
    }
}
