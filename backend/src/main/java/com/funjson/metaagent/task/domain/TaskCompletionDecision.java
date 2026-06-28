package com.funjson.metaagent.task.domain;

/**
 * Task 层验收决定。
 *
 * @param accepted 是否接受
 * @param retryable 是否允许重试
 * @param code 稳定决定码
 * @param summary 可审计摘要
 */
public record TaskCompletionDecision(
        boolean accepted,
        boolean retryable,
        String code,
        String summary) {
}
