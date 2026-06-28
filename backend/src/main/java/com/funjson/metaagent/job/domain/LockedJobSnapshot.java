package com.funjson.metaagent.job.domain;

import java.util.UUID;

import com.funjson.metaagent.capability.domain.CapabilityRef;

/**
 * Job 调度事务内锁定的 Job 领域快照。
 *
 * @param id Job ID
 * @param status Job 状态
 * @param version 乐观锁版本
 * @param providerId Provider ID
 * @param rootCapability AgentProfile 根 Capability
 */
public record LockedJobSnapshot(
        UUID id,
        JobStatus status,
        long version,
        String providerId,
        CapabilityRef rootCapability) {
}
