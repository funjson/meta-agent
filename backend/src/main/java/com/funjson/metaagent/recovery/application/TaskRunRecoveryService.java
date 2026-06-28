package com.funjson.metaagent.recovery.application;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.recovery.api.RecoveryPlanView;
import com.funjson.metaagent.recovery.domain.RecoveryCandidate;
import com.funjson.metaagent.recovery.domain.RecoveryDecision;
import com.funjson.metaagent.recovery.domain.RecoveryDisposition;
import com.funjson.metaagent.recovery.domain.RecoveryPolicy;
import com.funjson.metaagent.recovery.application.port.out.RecoveryStore;
import com.funjson.metaagent.loop.application.port.out.RuntimeStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 检查 TaskRun 中断并持久化恢复尝试。
 *
 * <p>该服务只生成可审计恢复计划；真实续跑由 ResumeExecutor 获取租约后执行，
 * 避免恢复检查接口直接重复外部副作用。</p>
 */
@Service
public class TaskRunRecoveryService {

    private final RecoveryStore repository;
    private final RecoveryPolicy policy;
    private final ObjectMapper objectMapper;
    private final RuntimeStore runtimeRepository;

    /**
     * 创建 TaskRun Recovery Service。
     *
     * @param repository Recovery Repository
     * @param policy Recovery Policy
     * @param objectMapper JSON 序列化器
     * @param runtimeRepository Runtime Event Repository
     */
    public TaskRunRecoveryService(
            RecoveryStore repository,
            RecoveryPolicy policy,
            ObjectMapper objectMapper,
            RuntimeStore runtimeRepository) {
        this.repository = repository;
        this.policy = policy;
        this.objectMapper = objectMapper;
        this.runtimeRepository = runtimeRepository;
    }

    /**
     * 只读检查 TaskRun 恢复边界。
     *
     * @param taskRunId TaskRun ID
     * @return 恢复计划
     */
    @Transactional(readOnly = true)
    public RecoveryPlanView inspect(UUID taskRunId) {
        RecoveryCandidate candidate =
                repository.requireCandidate(taskRunId);
        RecoveryDecision decision = policy.decide(candidate);
        return view(candidate, decision, null, null);
    }

    /**
     * 持久化一次恢复准备决策。
     *
     * @param taskRunId TaskRun ID
     * @return 带尝试 ID 的恢复计划
     */
    @Transactional
    public RecoveryPlanView prepare(UUID taskRunId) {
        RecoveryCandidate candidate =
                repository.requireCandidate(taskRunId);
        RecoveryDecision decision = policy.decide(candidate);
        UUID attemptId = UUID.randomUUID();
        String status = decision.disposition()
                == RecoveryDisposition.AUTO_RESUME
                ? "PREPARED"
                : "BLOCKED";
        repository.insertAttempt(
                attemptId,
                candidate,
                decision,
                status,
                json(Map.of(
                        "loopRunId",
                        value(candidate.loopRunId()),
                        "loopRunParentType",
                        value(candidate.loopRunParentType()),
                        "loopNodeId",
                        value(candidate.loopNodeId()),
                        "loopNodeStatus",
                        value(candidate.loopNodeStatus()),
                        "actionType",
                        value(candidate.actionType()),
                        "sideEffectClass",
                        value(candidate.sideEffectClass()))));
        runtimeRepository.insertRuntimeEvent(
                UUID.randomUUID(),
                candidate.jobId(),
                candidate.taskId(),
                candidate.taskRunId(),
                "RECOVERY_ATTEMPT",
                attemptId,
                "PREPARED".equals(status)
                        ? "RECOVERY_PREPARED"
                        : "RECOVERY_BLOCKED",
                json(Map.of(
                        "recoveryAttemptId", attemptId,
                        "checkpointId", value(candidate.checkpointId()),
                        "interruptionType",
                        decision.interruptionType().name(),
                        "disposition",
                        decision.disposition().name(),
                        "decisionCode", decision.code())));
        return view(
                candidate,
                decision,
                attemptId,
                status);
    }

    /** 创建 API 视图。 */
    private RecoveryPlanView view(
            RecoveryCandidate candidate,
            RecoveryDecision decision,
            UUID attemptId,
            String attemptStatus) {
        return new RecoveryPlanView(
                candidate.taskRunId(),
                candidate.checkpointId(),
                candidate.checkpointType(),
                decision.interruptionType(),
                decision.disposition(),
                decision.code(),
                decision.summary(),
                candidate.leaseOwner(),
                candidate.leaseUntil(),
                attemptId,
                attemptStatus);
    }

    /** 把可空值转换为可序列化文本。 */
    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 序列化恢复上下文。 */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize recovery context",
                    exception);
        }
    }
}
