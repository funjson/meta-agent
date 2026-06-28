package com.funjson.metaagent.job.domain;

import java.util.UUID;

/**
 * Child Job 物化事务内锁定的父 Job 快照。
 *
 * @param jobId 父 Job ID
 * @param rootJobId 根 Job ID
 * @param recursionDepth 父 Job 深度
 * @param agentProfileId AgentProfile ID
 * @param conversationId Conversation ID
 * @param sourceMessageId 根来源消息 ID
 * @param providerId 有效 Provider
 * @param status 父 Job 状态
 */
public record ChildJobParentSnapshot(
        UUID jobId,
        UUID rootJobId,
        int recursionDepth,
        String agentProfileId,
        UUID conversationId,
        UUID sourceMessageId,
        String providerId,
        JobStatus status) {
}
