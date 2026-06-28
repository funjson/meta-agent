package com.funjson.metaagent.task.domain;

import com.funjson.metaagent.loop.domain.LoopOutcome;

/**
 * Task 层根据 Task Contract、Loop Outcome、产物和重试条件决定是否完成 Task。
 */
public interface TaskCompletionPolicy {

    /**
     * 验收一次 TaskRun 的 Loop Outcome。
     *
     * @param outcome Loop Kernel 输出
     * @return Task 验收决定
     */
    TaskCompletionDecision evaluate(LoopOutcome outcome);
}
