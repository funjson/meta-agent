package com.funjson.metaagent.recovery.application;

import java.time.Duration;
import java.util.UUID;

import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.recovery.application.port.out.RecoveryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理 TaskRun 的 Worker 租约和心跳。
 */
@Service
public class RuntimeLeaseService {

    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    private final RecoveryStore repository;
    private final String workerId = "worker-" + UUID.randomUUID();

    /**
     * 创建 Runtime Lease Service。
     *
     * @param repository Recovery Repository
     */
    public RuntimeLeaseService(RecoveryStore repository) {
        this.repository = repository;
    }

    /**
     * 获取或续租 TaskRun。
     *
     * @param taskRunId TaskRun ID
     */
    @Transactional
    public void acquire(UUID taskRunId) {
        if (!repository.acquireLease(
                taskRunId,
                workerId,
                LEASE_DURATION)) {
            throw new RuntimeStateException(
                    "TASK_RUN_LEASE_CONFLICT",
                    "TaskRun is owned by another active worker");
        }
    }

    /**
     * 刷新 TaskRun 心跳。
     *
     * @param taskRunId TaskRun ID
     */
    @Transactional
    public void heartbeat(UUID taskRunId) {
        if (!repository.heartbeat(
                taskRunId,
                workerId,
                LEASE_DURATION)) {
            throw new RuntimeStateException(
                    "TASK_RUN_LEASE_LOST",
                    "TaskRun lease is no longer owned by this worker");
        }
    }

    /**
     * 释放 TaskRun 租约。
     *
     * @param taskRunId TaskRun ID
     */
    @Transactional
    public void release(UUID taskRunId) {
        repository.releaseLease(taskRunId, workerId);
    }
}
