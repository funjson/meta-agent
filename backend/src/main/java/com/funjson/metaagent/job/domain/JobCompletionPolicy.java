package com.funjson.metaagent.job.domain;

/**
 * Job 层根据 TaskGraph 与全局约束决定 Job 是否完成。
 */
public interface JobCompletionPolicy {

    /**
     * 评估 TaskGraph 当前状态。
     *
     * @param incompleteTaskCount 未完成 Task 数
     * @param readyTaskCount READY Task 数
     * @return Job 验收决定
     */
    JobCompletionDecision evaluate(
            long incompleteTaskCount,
            long readyTaskCount);
}
