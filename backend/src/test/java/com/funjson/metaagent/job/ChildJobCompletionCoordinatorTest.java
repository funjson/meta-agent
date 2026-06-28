package com.funjson.metaagent.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.job.application.ChildJobCompletionCoordinator;
import com.funjson.metaagent.job.application.port.out.ChildJobStore;
import com.funjson.metaagent.job.domain.ChildJobCompletionSnapshot;
import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.loop.application.port.out.RuntimeStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Child Job 终态可以原子形成父恢复命令。
 */
class ChildJobCompletionCoordinatorTest {

    @Test
    void persistsOutcomeBeforeReturningResumeCommand() {
        ChildJobStore store = mock(ChildJobStore.class);
        RuntimeStore runtimeStore = mock(RuntimeStore.class);
        UUID childJobId = UUID.randomUUID();
        UUID parentJobId = UUID.randomUUID();
        UUID taskRunId = UUID.randomUUID();
        UUID loopNodeId = UUID.randomUUID();
        when(store.lockCompletion(childJobId)).thenReturn(Optional.of(
                new ChildJobCompletionSnapshot(
                        UUID.randomUUID(),
                        parentJobId,
                        childJobId,
                        taskRunId,
                        loopNodeId,
                        "RUNNING",
                        JobStatus.COMPLETED)));
        when(store.summarizeChildJob(childJobId))
                .thenReturn("done");
        when(store.countChildJobEvidence(childJobId))
                .thenReturn(2);
        when(store.completeDerivation(eq(childJobId), any()))
                .thenReturn(true);

        var command = new ChildJobCompletionCoordinator(
                store,
                runtimeStore,
                new ObjectMapper()).prepare(childJobId).orElseThrow();

        assertThat(command.parentJobId()).isEqualTo(parentJobId);
        assertThat(command.outcome().evidenceCount()).isEqualTo(2);
        verify(store).clearOriginLoopNode(loopNodeId, childJobId);
        verify(runtimeStore).insertOutboxEvent(
                any(),
                eq("CHILD_JOB_COMPLETED"),
                any());
    }
}
