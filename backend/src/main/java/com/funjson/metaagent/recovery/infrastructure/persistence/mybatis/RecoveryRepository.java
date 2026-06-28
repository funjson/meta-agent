package com.funjson.metaagent.recovery.infrastructure.persistence.mybatis;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.runtime.domain.ChildJobOutcome;
import com.funjson.metaagent.runtime.domain.ClarificationAnswerOutcome;
import com.funjson.metaagent.loop.domain.LoopActionType;
import com.funjson.metaagent.loop.domain.LoopRunParentType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.recovery.domain.RecoveryCandidate;
import com.funjson.metaagent.recovery.domain.RecoveryDecision;
import com.funjson.metaagent.recovery.domain.ResumeExecutionSnapshot;
import com.funjson.metaagent.recovery.domain.LoopNodeResumeContext;
import com.funjson.metaagent.recovery.application.port.out.RecoveryStore;
import org.springframework.stereotype.Repository;

/**
 * 适配 Recovery Application 与 MyBatis 持久化。
 */
@Repository
public class RecoveryRepository implements RecoveryStore {

    private final RecoveryPersistenceMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建 Recovery Repository。
     *
     * @param mapper Recovery Mapper
     * @param objectMapper JSON Mapper
     */
    public RecoveryRepository(
            RecoveryPersistenceMapper mapper,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /** @return 是否获取租约 */
    public boolean acquireLease(
            UUID taskRunId,
            String workerId,
            Duration duration) {
        return mapper.acquireLease(
                taskRunId,
                workerId,
                duration.toSeconds()) == 1;
    }

    /** @return 是否刷新心跳 */
    public boolean heartbeat(
            UUID taskRunId,
            String workerId,
            Duration duration) {
        return mapper.heartbeat(
                taskRunId,
                workerId,
                duration.toSeconds()) == 1;
    }

    /** 释放租约。 */
    public void releaseLease(UUID taskRunId, String workerId) {
        mapper.releaseLease(taskRunId, workerId);
    }

    /**
     * 查询恢复候选。
     *
     * @param taskRunId TaskRun ID
     * @return 候选
     */
    public RecoveryCandidate requireCandidate(UUID taskRunId) {
        Map<String, Object> row =
                mapper.findRecoveryCandidate(taskRunId);
        if (row == null) {
            throw new RuntimeStateException(
                    "TASK_RUN_NOT_FOUND",
                    "TaskRun not found: " + taskRunId);
        }
        return new RecoveryCandidate(
                uuid(row.get("taskRunId")),
                uuid(row.get("jobId")),
                uuid(row.get("taskId")),
                text(row.get("taskRunStatus")),
                nullableText(row.get("leaseOwner")),
                instant(row.get("leaseUntil")),
                uuid(row.get("checkpointId")),
                nullableText(row.get("checkpointType")),
                booleanValue(row.get("checkpointRestorable")),
                booleanValue(row.get("checkpointChecksumValid")),
                uuid(row.get("loopRunId")),
                nullableText(row.get("loopRunParentType")),
                uuid(row.get("loopNodeId")),
                nullableText(row.get("loopNodeStatus")),
                nullableText(row.get("actionType")),
                nullableText(row.get("sideEffectClass")));
    }

    /**
     * 重建 ResumeExecutor 需要的 LoopNode 游标。
     *
     * @param taskRunId TaskRun ID
     * @return 恢复快照
     */
    public ResumeExecutionSnapshot requireResumeSnapshot(UUID taskRunId) {
        Map<String, Object> row =
                mapper.findResumeSnapshot(taskRunId);
        if (row == null) {
            throw new RuntimeStateException(
                    "RESUME_SNAPSHOT_NOT_FOUND",
                    "Resume snapshot is unavailable: " + taskRunId);
        }
        RunExecutionContext context = new RunExecutionContext(
                uuid(row.get("jobId")),
                uuid(row.get("taskId")),
                uuid(row.get("taskRunId")),
                uuid(row.get("loopRunId")),
                uuid(row.get("loopNodeId")),
                uuid(row.get("parentNodeId")),
                number(row.get("depth")).intValue(),
                number(row.get("iterationNo")).intValue(),
                LoopRunParentType.valueOf(
                        text(row.get("loopRunParentType"))),
                uuid(row.get("loopRunParentId")),
                number(row.get("recursionDepth")).intValue(),
                text(row.get("providerId")),
                text(row.get("goal")),
                text(row.get("feedback")),
                null);
        return new ResumeExecutionSnapshot(
                uuid(row.get("checkpointId")),
                text(row.get("checkpointType")),
                context,
                LoopActionType.valueOf(text(row.get("actionType"))),
                text(row.get("completionCriterion")),
                number(row.get("maxTokens")).intValue(),
                uuid(row.get("actionPhaseId")),
                nullableText(row.get("actionPhaseStatus")));
    }

    /**
     * 重建等待子执行的 origin LoopNode 上下文。
     *
     * @param loopNodeId origin LoopNode ID
     * @return 恢复上下文
     */
    public LoopNodeResumeContext requireLoopNodeResumeContext(
            UUID loopNodeId) {
        Map<String, Object> row =
                mapper.findLoopNodeResumeContext(loopNodeId);
        if (row == null) {
            throw new RuntimeStateException(
                    "ORIGIN_LOOP_NODE_NOT_FOUND",
                    "Origin LoopNode not found: " + loopNodeId);
        }
        RunExecutionContext context = new RunExecutionContext(
                uuid(row.get("jobId")),
                uuid(row.get("taskId")),
                uuid(row.get("taskRunId")),
                uuid(row.get("loopRunId")),
                uuid(row.get("loopNodeId")),
                uuid(row.get("parentNodeId")),
                number(row.get("depth")).intValue(),
                number(row.get("iterationNo")).intValue(),
                LoopRunParentType.valueOf(
                        text(row.get("loopRunParentType"))),
                uuid(row.get("loopRunParentId")),
                number(row.get("recursionDepth")).intValue(),
                text(row.get("providerId")),
                text(row.get("goal")),
                text(row.get("feedback")),
                null);
        return new LoopNodeResumeContext(
                context,
                text(row.get("completionCriterion")));
    }

    /**
     * 读取已持久化的 ChildJobOutcome。
     *
     * @param loopNodeId origin LoopNode ID
     * @return ChildJobOutcome
     */
    public ChildJobOutcome requireCompletedChildJobOutcome(
            UUID loopNodeId) {
        String json = mapper.findCompletedChildJobOutcome(loopNodeId);
        if (json == null) {
            throw new RuntimeStateException(
                    "CHILD_JOB_OUTCOME_NOT_READY",
                    "ChildJobOutcome is unavailable: " + loopNodeId);
        }
        try {
            return objectMapper.readValue(json, ChildJobOutcome.class);
        } catch (JsonProcessingException exception) {
            throw new RuntimeStateException(
                    "CHILD_JOB_OUTCOME_INVALID",
                    "ChildJobOutcome cannot be parsed");
        }
    }

    /**
     * 读取已经绑定到 origin LoopNode 的澄清回答。
     *
     * @param loopNodeId origin LoopNode ID
     * @return 澄清回答结果
     */
    public ClarificationAnswerOutcome requireAnsweredClarificationOutcome(
            UUID loopNodeId) {
        Map<String, Object> row =
                mapper.findAnsweredClarificationOutcome(loopNodeId);
        if (row == null) {
            throw new RuntimeStateException(
                    "CLARIFICATION_OUTCOME_NOT_READY",
                    "Clarification answer is unavailable: " + loopNodeId);
        }
        return new ClarificationAnswerOutcome(
                uuid(row.get("clarificationRequestId")),
                text(row.get("question")),
                text(row.get("answer")));
    }

    /** {@inheritDoc} */
    public List<UUID> findAutoRecoveryCandidates(int limit) {
        return mapper.findAutoRecoveryCandidates(limit).stream()
                .map(UUID::fromString)
                .toList();
    }

    /**
     * 插入恢复尝试审计。
     *
     * @param attemptId 尝试 ID
     * @param candidate 候选
     * @param decision 决策
     * @param status 尝试状态
     * @param contextJson 决策上下文
     */
    public void insertAttempt(
            UUID attemptId,
            RecoveryCandidate candidate,
            RecoveryDecision decision,
            String status,
            String contextJson) {
        mapper.insertRecoveryAttempt(
                attemptId,
                candidate.taskRunId(),
                candidate.checkpointId(),
                decision.interruptionType().name(),
                decision.disposition().name(),
                decision.code(),
                status,
                contextJson);
    }

    /**
     * 更新恢复尝试状态。
     *
     * @param attemptId 尝试 ID
     * @param status 状态
     * @param contextJson 结果上下文
     */
    public void updateAttempt(
            UUID attemptId,
            String status,
            String contextJson) {
        if (mapper.updateRecoveryAttempt(
                attemptId,
                status,
                contextJson) != 1) {
            throw new RuntimeStateException(
                    "RECOVERY_ATTEMPT_NOT_FOUND",
                    "Recovery attempt not found: " + attemptId);
        }
    }

    /** 转换 UUID。 */
    private UUID uuid(Object value) {
        return value == null
                ? null
                : UUID.fromString(String.valueOf(value));
    }

    /** 读取字符串。 */
    private String text(Object value) {
        return String.valueOf(value);
    }

    /** 读取可空字符串。 */
    private String nullableText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 读取布尔值。 */
    private boolean booleanValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return ((Number) value).intValue() != 0;
    }

    /** 读取数值。 */
    private Number number(Object value) {
        return (Number) value;
    }

    /** 转换时间。 */
    private Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.atZone(java.time.ZoneOffset.UTC).toInstant();
        }
        throw new IllegalArgumentException(
                "Unsupported recovery timestamp: " + value);
    }
}
