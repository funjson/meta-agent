package com.funjson.metaagent.job.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.job.application.port.out.ChildJobStore;
import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.loop.application.port.out.RuntimeStore;
import com.funjson.metaagent.runtime.domain.ChildJobOutcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 将 Child Job 终态原子转换为可重放 ChildJobOutcome。
 */
@Service
public class ChildJobCompletionCoordinator {

    private final ChildJobStore store;
    private final RuntimeStore runtimeStore;
    private final ObjectMapper objectMapper;

    /**
     * 创建 Child Job 完成协调器。
     *
     * @param store Child Job Store
     * @param runtimeStore Runtime 事件 Store
     * @param objectMapper JSON Mapper
     */
    public ChildJobCompletionCoordinator(
            ChildJobStore store,
            RuntimeStore runtimeStore,
            ObjectMapper objectMapper) {
        this.store = store;
        this.runtimeStore = runtimeStore;
        this.objectMapper = objectMapper;
    }

    /**
     * 幂等写入 Outcome 和完成事件，返回父恢复命令。
     *
     * @param childJobId Child Job ID
     * @return 父恢复命令；根 Job 或已处理时为空
     */
    @Transactional
    public Optional<ParentResumeCommand> prepare(UUID childJobId) {
        var optionalSnapshot = store.lockCompletion(childJobId);
        if (optionalSnapshot.isEmpty()) {
            return Optional.empty();
        }
        var snapshot = optionalSnapshot.get();
        if (!"RUNNING".equals(snapshot.derivationStatus())) {
            return Optional.empty();
        }
        ChildJobOutcome outcome = new ChildJobOutcome(
                childJobId,
                outcomeStatus(snapshot.childJobStatus()),
                store.summarizeChildJob(childJobId),
                Map.of(),
                store.countChildJobEvidence(childJobId));
        String outcomeJson = json(outcome);
        if (!store.completeDerivation(childJobId, outcomeJson)) {
            return Optional.empty();
        }
        store.clearOriginLoopNode(
                snapshot.originLoopNodeId(),
                childJobId);

        String payload = json(Map.of(
                "derivationId", snapshot.derivationId(),
                "parentJobId", snapshot.parentJobId(),
                "childJobId", childJobId,
                "originTaskRunId", snapshot.originTaskRunId(),
                "originLoopNodeId", snapshot.originLoopNodeId(),
                "outcome", outcome));
        runtimeStore.insertRuntimeEvent(
                UUID.randomUUID(),
                snapshot.parentJobId(),
                null,
                snapshot.originTaskRunId(),
                "JOB_DERIVATION",
                snapshot.derivationId(),
                "CHILD_JOB_COMPLETED",
                payload);
        runtimeStore.insertOutboxEvent(
                UUID.randomUUID(),
                "CHILD_JOB_COMPLETED",
                payload);
        return Optional.of(new ParentResumeCommand(
                snapshot.parentJobId(),
                snapshot.originTaskRunId(),
                snapshot.originLoopNodeId(),
                outcome));
    }

    /** 映射允许回传的 Child Job 终态。 */
    private ChildJobOutcome.Status outcomeStatus(JobStatus status) {
        return switch (status) {
            case COMPLETED -> ChildJobOutcome.Status.COMPLETED;
            case FAILED -> ChildJobOutcome.Status.FAILED;
            case CANCELLED -> ChildJobOutcome.Status.CANCELLED;
            default -> throw new IllegalStateException(
                    "Child Job is not terminal: " + status);
        };
    }

    /** 序列化完成事实。 */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize ChildJobOutcome",
                    exception);
        }
    }

    /**
     * 父 TaskRun 恢复命令。
     *
     * @param parentJobId 父 Job ID
     * @param originTaskRunId origin TaskRun ID
     * @param originLoopNodeId origin LoopNode ID
     * @param outcome ChildJobOutcome
     */
    public record ParentResumeCommand(
            UUID parentJobId,
            UUID originTaskRunId,
            UUID originLoopNodeId,
            ChildJobOutcome outcome) {
    }
}
