package com.funjson.metaagent.loop.domain;

import java.util.UUID;

import com.funjson.metaagent.runtime.domain.ChildJobRequest;

/**
 * Loop Kernel 提交给上层协调器的执行结果。
 *
 * @param context 执行上下文
 * @param status 结果状态
 * @param resultSummary 结果摘要
 * @param evidenceId 完成证据
 * @param failureSummary 失败摘要
 * @param childJobRequest 等待物化的 Child Job 请求
 */
public record LoopOutcome(
        RunExecutionContext context,
        OutcomeStatus status,
        String resultSummary,
        UUID evidenceId,
        String failureSummary,
        ChildJobRequest childJobRequest) {

    /**
     * 创建成功结果。
     *
     * @param context 执行上下文
     * @param resultSummary 结果摘要
     * @param evidenceId Evidence ID
     * @return 成功结果
     */
    public static LoopOutcome completed(
            RunExecutionContext context,
            String resultSummary,
            UUID evidenceId) {
        return new LoopOutcome(
                context,
                OutcomeStatus.COMPLETED,
                resultSummary,
                evidenceId,
                null,
                null);
    }

    /**
     * 创建失败结果。
     *
     * @param context 执行上下文
     * @param failureSummary 失败摘要
     * @return 失败结果
     */
    public static LoopOutcome failed(
            RunExecutionContext context,
            String failureSummary) {
        return new LoopOutcome(
                context,
                OutcomeStatus.FAILED,
                null,
                null,
                failureSummary,
                null);
    }

    /**
     * 创建等待阻塞型 Child Job 的结果。
     *
     * @param context origin LoopNode 上下文
     * @param request Child Job 请求
     * @return 等待结果
     */
    public static LoopOutcome waitingChildJob(
            RunExecutionContext context,
            ChildJobRequest request) {
        return new LoopOutcome(
                context,
                OutcomeStatus.WAITING_CHILD_JOB,
                null,
                null,
                null,
                request);
    }

    /**
     * 创建等待用户澄清回答的结果。
     *
     * @param context origin LoopNode 上下文
     * @return 等待人工回答结果
     */
    public static LoopOutcome waitingHuman(RunExecutionContext context) {
        return new LoopOutcome(
                context,
                OutcomeStatus.WAITING_HUMAN,
                null,
                null,
                null,
                null);
    }

    /**
     * LoopOutcome 状态。
     */
    public enum OutcomeStatus {
        COMPLETED,
        FAILED,
        WAITING_CHILD_JOB,
        WAITING_HUMAN
    }
}
