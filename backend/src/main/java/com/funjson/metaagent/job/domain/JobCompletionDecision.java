package com.funjson.metaagent.job.domain;

/**
 * Job 层验收与调度决定。
 *
 * @param completed Job 是否完成
 * @param blocked 是否存在不可推进的未完成图
 * @param code 稳定决定码
 */
public record JobCompletionDecision(
        boolean completed,
        boolean blocked,
        String code) {
}
