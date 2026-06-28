package com.funjson.metaagent.task.domain;

import com.funjson.metaagent.loop.domain.LoopOutcome;

/**
 * v0.1 默认 Task 验收策略。
 *
 * <p>该基础实现要求 Loop 已完成且提供 Evidence，不使用“非空文本即完成”。</p>
 */
public class DefaultTaskCompletionPolicy
        implements TaskCompletionPolicy {

    /** {@inheritDoc} */
    @Override
    public TaskCompletionDecision evaluate(LoopOutcome outcome) {
        if (outcome.status()
                == LoopOutcome.OutcomeStatus.WAITING_CHILD_JOB) {
            return new TaskCompletionDecision(
                    false,
                    false,
                    "WAITING_CHILD_JOB",
                    "TaskRun 正在等待阻塞型 Child Job");
        }
        if (outcome.status() != LoopOutcome.OutcomeStatus.COMPLETED) {
            return new TaskCompletionDecision(
                    false,
                    true,
                    "LOOP_NOT_COMPLETED",
                    "Loop 尚未提交可验收完成结果");
        }
        if (outcome.evidenceId() == null) {
            return new TaskCompletionDecision(
                    false,
                    true,
                    "COMPLETION_EVIDENCE_REQUIRED",
                    "Task 完成必须提供 Evidence");
        }
        return new TaskCompletionDecision(
                true,
                false,
                "TASK_ACCEPTED",
                "Loop Outcome 与 Evidence 满足基础 Task Contract");
    }
}
