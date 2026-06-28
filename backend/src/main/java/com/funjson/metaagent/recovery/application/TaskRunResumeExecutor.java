package com.funjson.metaagent.recovery.application;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.job.application.JobCompletionCoordinator;
import com.funjson.metaagent.task.api.TaskRunView;
import com.funjson.metaagent.task.application.TaskRunQueryService;
import com.funjson.metaagent.loop.application.RuntimeExecutionService;
import com.funjson.metaagent.loop.application.RuntimeTransactionService;
import com.funjson.metaagent.loop.domain.LoopExecutionException;
import com.funjson.metaagent.loop.domain.LoopOutcome;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.loop.application.port.out.RuntimeStore;
import com.funjson.metaagent.recovery.domain.RecoveryCandidate;
import com.funjson.metaagent.recovery.domain.RecoveryDecision;
import com.funjson.metaagent.recovery.domain.RecoveryDisposition;
import com.funjson.metaagent.recovery.domain.RecoveryPolicy;
import com.funjson.metaagent.recovery.domain.ResumeExecutionSnapshot;
import com.funjson.metaagent.recovery.application.port.out.RecoveryStore;
import org.springframework.stereotype.Service;

/**
 * 从支持的安全 Checkpoint 真正续跑 TaskRun。
 */
@Service
public class TaskRunResumeExecutor {

    private final RecoveryStore recoveryRepository;
    private final RecoveryPolicy recoveryPolicy;
    private final RuntimeLeaseService leaseService;
    private final RuntimeExecutionService runtimeExecutionService;
    private final RuntimeTransactionService transactions;
    private final TaskRunQueryService taskRunQueries;
    private final JobCompletionCoordinator completionService;
    private final RuntimeStore runtimeRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建 TaskRun Resume Executor。
     *
     * @param recoveryRepository Recovery Repository
     * @param recoveryPolicy Recovery Policy
     * @param leaseService Lease Service
     * @param runtimeExecutionService Loop Kernel
     * @param transactions Loop 事务服务
     * @param taskRunQueries TaskRun 查询服务
     * @param completionService Job Completion Coordinator
     * @param runtimeRepository Runtime Event Repository
     * @param objectMapper JSON 序列化器
     */
    public TaskRunResumeExecutor(
            RecoveryStore recoveryRepository,
            RecoveryPolicy recoveryPolicy,
            RuntimeLeaseService leaseService,
            RuntimeExecutionService runtimeExecutionService,
            RuntimeTransactionService transactions,
            TaskRunQueryService taskRunQueries,
            JobCompletionCoordinator completionService,
            RuntimeStore runtimeRepository,
            ObjectMapper objectMapper) {
        this.recoveryRepository = recoveryRepository;
        this.recoveryPolicy = recoveryPolicy;
        this.leaseService = leaseService;
        this.runtimeExecutionService = runtimeExecutionService;
        this.transactions = transactions;
        this.taskRunQueries = taskRunQueries;
        this.completionService = completionService;
        this.runtimeRepository = runtimeRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取租约并从最新安全点续跑 TaskRun。
     *
     * @param taskRunId TaskRun ID
     * @return 完成后的 TaskRun
     */
    public TaskRunView resume(UUID taskRunId) {
        RecoveryCandidate candidate =
                recoveryRepository.requireCandidate(taskRunId);
        RecoveryDecision decision = recoveryPolicy.decide(candidate);
        if (decision.disposition()
                != RecoveryDisposition.AUTO_RESUME) {
            throw new RuntimeStateException(
                    decision.code(),
                    decision.summary());
        }

        UUID attemptId = UUID.randomUUID();
        recoveryRepository.insertAttempt(
                attemptId,
                candidate,
                decision,
                "RUNNING",
                json(Map.of(
                        "checkpointId", candidate.checkpointId(),
                        "checkpointType", candidate.checkpointType())));
        try {
            // 恢复判定和租约获取之间存在竞态；抢租约失败也必须形成终态审计。
            leaseService.acquire(taskRunId);
        } catch (RuntimeException failure) {
            failAttempt(
                    candidate,
                    attemptId,
                    decision,
                    failure);
            throw failure;
        }
        try {
            ResumeExecutionSnapshot snapshot =
                    recoveryRepository.requireResumeSnapshot(taskRunId);
            LoopOutcome outcome = execute(snapshot);
            if (outcome.status()
                    != LoopOutcome.OutcomeStatus.WAITING_CHILD_JOB
                    && outcome.status()
                    != LoopOutcome.OutcomeStatus.WAITING_HUMAN) {
                completionService.accept(outcome);
            }
            recoveryRepository.updateAttempt(
                    attemptId,
                    "COMPLETED",
                    json(Map.of(
                            "checkpointId", snapshot.checkpointId(),
                            "loopRunId", snapshot.context().loopRunId(),
                            "loopNodeId", snapshot.context().loopNodeId(),
                            "outcomeStatus", outcome.status().name(),
                            "evidenceId",
                            outcome.evidenceId() == null
                                    ? ""
                                    : outcome.evidenceId())));
            insertEvent(
                    candidate,
                    attemptId,
                    "RECOVERY_COMPLETED",
                    decision);
            return taskRunQueries.get(taskRunId);
        } catch (LoopExecutionException failure) {
            LoopOutcome failedOutcome = transactions.fail(
                    failure.context(),
                    failure.originalFailure());
            completionService.reject(failedOutcome);
            failAttempt(
                    candidate,
                    attemptId,
                    decision,
                    failure.originalFailure());
            throw failure.originalFailure();
        } catch (RuntimeException failure) {
            failAttempt(
                    candidate,
                    attemptId,
                    decision,
                    failure);
            throw failure;
        } finally {
            leaseService.release(taskRunId);
        }
    }

    /** 按 Checkpoint 游标调用 Loop Kernel。 */
    private LoopOutcome execute(ResumeExecutionSnapshot snapshot) {
        return switch (snapshot.checkpointType()) {
            case "RUN_START", "CHILD_LOOP_CREATED" ->
                    runtimeExecutionService.execute(snapshot.context());
            case "ACTION_PREPARED" ->
                    runtimeExecutionService.resumePreparedModelAction(
                            snapshot);
            case "CHILD_JOB_CREATED" ->
                    runtimeExecutionService.completeRecoveredChildJobAction(
                            recoveryRepository
                                    .requireLoopNodeResumeContext(
                                            snapshot.context()
                                                    .loopNodeId()),
                            recoveryRepository
                                    .requireCompletedChildJobOutcome(
                                            snapshot.context()
                                                    .loopNodeId()));
            case "CLARIFICATION_ANSWERED" ->
                    runtimeExecutionService
                            .completeRecoveredClarificationAction(
                                    recoveryRepository
                                            .requireLoopNodeResumeContext(
                                                    snapshot.context()
                                                            .loopNodeId()),
                                    recoveryRepository
                                            .requireAnsweredClarificationOutcome(
                                                    snapshot.context()
                                                            .loopNodeId()));
            default -> throw new RuntimeStateException(
                    "CHECKPOINT_CURSOR_UNSUPPORTED",
                    "Unsupported checkpoint cursor: "
                            + snapshot.checkpointType());
        };
    }

    /** 标记恢复尝试失败。 */
    private void failAttempt(
            RecoveryCandidate candidate,
            UUID attemptId,
            RecoveryDecision decision,
            RuntimeException failure) {
        recoveryRepository.updateAttempt(
                attemptId,
                "FAILED",
                json(Map.of(
                        "errorType", failure.getClass().getSimpleName(),
                        "message",
                        failure.getMessage() == null
                                ? "resume failed"
                                : failure.getMessage())));
        insertEvent(
                candidate,
                attemptId,
                "RECOVERY_FAILED",
                decision);
    }

    /** 写入恢复事件。 */
    private void insertEvent(
            RecoveryCandidate candidate,
            UUID attemptId,
            String eventType,
            RecoveryDecision decision) {
        runtimeRepository.insertRuntimeEvent(
                UUID.randomUUID(),
                candidate.jobId(),
                candidate.taskId(),
                candidate.taskRunId(),
                "RECOVERY_ATTEMPT",
                attemptId,
                eventType,
                json(Map.of(
                        "recoveryAttemptId", attemptId,
                        "checkpointId", candidate.checkpointId(),
                        "decisionCode", decision.code())));
    }

    /** 序列化恢复状态。 */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize resume state",
                    exception);
        }
    }
}
