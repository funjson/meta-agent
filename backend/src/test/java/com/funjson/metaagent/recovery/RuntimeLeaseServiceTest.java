package com.funjson.metaagent.recovery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.recovery.application.RuntimeLeaseService;
import com.funjson.metaagent.recovery.infrastructure.persistence.mybatis.RecoveryRepository;
import org.junit.jupiter.api.Test;

/**
 * 验证 TaskRun Worker 租约冲突和心跳丢失处理。
 */
class RuntimeLeaseServiceTest {

    @Test
    void rejectsLeaseOwnedByAnotherWorker() {
        RecoveryRepository repository = mock(RecoveryRepository.class);
        UUID taskRunId = UUID.randomUUID();
        when(repository.acquireLease(
                eq(taskRunId),
                anyString(),
                any())).thenReturn(false);
        RuntimeLeaseService service = new RuntimeLeaseService(repository);

        assertThatThrownBy(() -> service.acquire(taskRunId))
                .isInstanceOf(RuntimeStateException.class)
                .extracting("code")
                .isEqualTo("TASK_RUN_LEASE_CONFLICT");
    }

    @Test
    void heartbeatsAndReleasesOwnedLease() {
        RecoveryRepository repository = mock(RecoveryRepository.class);
        UUID taskRunId = UUID.randomUUID();
        when(repository.acquireLease(
                eq(taskRunId),
                anyString(),
                any())).thenReturn(true);
        when(repository.heartbeat(
                eq(taskRunId),
                anyString(),
                any())).thenReturn(true);
        RuntimeLeaseService service = new RuntimeLeaseService(repository);

        service.acquire(taskRunId);
        service.heartbeat(taskRunId);
        service.release(taskRunId);

        verify(repository).releaseLease(
                eq(taskRunId),
                anyString());
    }
}
