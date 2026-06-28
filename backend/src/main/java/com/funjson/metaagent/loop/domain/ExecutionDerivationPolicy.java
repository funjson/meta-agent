package com.funjson.metaagent.loop.domain;

import com.funjson.metaagent.runtime.domain.ChildJobRequest;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;

/**
 * 校验 Child Loop 与 Child Job 请求在 Loop 层可验证的边界。
 *
 * <p>递归深度、子 Job 数量、预算和权限由 JobCoordinator 在物化事务中再次校验。</p>
 */
public class ExecutionDerivationPolicy {

    /**
     * 校验 Child Job 请求具有合法 origin 和稳定幂等合同。
     *
     * @param origin origin LoopNode 上下文
     * @param request Child Job 请求
     */
    public void requireChildJobRequest(
            RunExecutionContext origin,
            ChildJobRequest request) {
        if (origin.loopRunId() == null || origin.loopNodeId() == null) {
            throw new RuntimeStateException(
                    "INVALID_DERIVATION_ORIGIN",
                    "Child Job request requires an origin LoopNode");
        }
        if (request == null) {
            throw new RuntimeStateException(
                    "INVALID_CHILD_JOB_REQUEST",
                    "Child Job request is required");
        }
    }
}
