package com.funjson.metaagent.job.domain;

/**
 * v0.1 默认 Job 验收策略。
 */
public class DefaultJobCompletionPolicy
        implements JobCompletionPolicy {

    /** {@inheritDoc} */
    @Override
    public JobCompletionDecision evaluate(
            long incompleteTaskCount,
            long readyTaskCount) {
        if (incompleteTaskCount == 0) {
            return new JobCompletionDecision(
                    true,
                    false,
                    "JOB_COMPLETED");
        }
        if (readyTaskCount == 0) {
            return new JobCompletionDecision(
                    false,
                    true,
                    "TASK_GRAPH_BLOCKED");
        }
        return new JobCompletionDecision(
                false,
                false,
                "TASK_GRAPH_CONTINUES");
    }
}
