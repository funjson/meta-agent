package com.funjson.metaagent.loop.domain;

import com.funjson.metaagent.runtime.domain.ChildJobRequest;

/**
 * LoopNode Evaluation/Adjustment 产生的结构化派生请求。
 *
 * @param type 派生类型
 * @param idempotencyKey 派生幂等键
 * @param reason 可审计短理由
 * @param childGoal Child LoopNode 目标
 * @param feedback 继承反馈
 * @param childJobRequest 阻塞型子 Job 请求
 */
public record ExecutionDerivationRequest(
        ExecutionDerivationType type,
        String idempotencyKey,
        String reason,
        String childGoal,
        String feedback,
        ChildJobRequest childJobRequest) {

    /**
     * 创建 Child LoopNode 派生请求。
     *
     * @param idempotencyKey 幂等键
     * @param reason 理由
     * @param childGoal 子目标
     * @param feedback 反馈
     * @return 请求
     */
    public static ExecutionDerivationRequest childLoop(
            String idempotencyKey,
            String reason,
            String childGoal,
            String feedback) {
        return new ExecutionDerivationRequest(
                ExecutionDerivationType.CHILD_LOOP,
                idempotencyKey,
                reason,
                childGoal,
                feedback,
                null);
    }

    /**
     * 创建阻塞型 Child Job 派生请求。
     *
     * @param reason 可审计理由
     * @param request Child Job 中立合同
     * @return 请求
     */
    public static ExecutionDerivationRequest childJob(
            String reason,
            ChildJobRequest request) {
        return new ExecutionDerivationRequest(
                ExecutionDerivationType.CHILD_JOB,
                request.idempotencyKey(),
                reason,
                null,
                null,
                request);
    }

    /**
     * 校验派生请求通用字段。
     */
    public ExecutionDerivationRequest {
        if (type == null) {
            throw new IllegalArgumentException("Derivation type is required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Derivation idempotency key is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Derivation reason is required");
        }
        if (type == ExecutionDerivationType.CHILD_LOOP) {
            if (childGoal == null || childGoal.isBlank()) {
                throw new IllegalArgumentException(
                        "Child Loop goal is required");
            }
            feedback = feedback == null ? "" : feedback;
        }
        if (type == ExecutionDerivationType.CHILD_JOB
                && childJobRequest == null) {
            throw new IllegalArgumentException(
                    "Child Job request is required");
        }
    }
}
