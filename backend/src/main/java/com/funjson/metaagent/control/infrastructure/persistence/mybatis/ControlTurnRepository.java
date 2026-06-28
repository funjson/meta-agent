package com.funjson.metaagent.control.infrastructure.persistence.mybatis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.control.api.ControlDecisionView;
import com.funjson.metaagent.control.application.port.out.ControlTurnStore;
import com.funjson.metaagent.control.domain.ControlTurn;
import com.funjson.metaagent.control.domain.ControlTurnStatus;
import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ControlTurnStore 的 MyBatis 适配器。
 */
@Repository
public class ControlTurnRepository implements ControlTurnStore {

    private final ControlTurnPersistenceMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建 ControlTurn Repository。
     *
     * @param mapper MyBatis Mapper
     * @param objectMapper JSON Mapper
     */
    public ControlTurnRepository(
            ControlTurnPersistenceMapper mapper,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    public Optional<ControlTurn> findByIdempotencyKey(
            String idempotencyKey) {
        return Optional.ofNullable(
                        mapper.findTurnByIdempotencyKey(idempotencyKey))
                .map(this::toTurn);
    }

    /** {@inheritDoc} */
    public Optional<ControlDecisionView> findDecision(
            UUID controlTurnId) {
        return Optional.ofNullable(mapper.findDecision(controlTurnId))
                .map(this::toDecision);
    }

    /** {@inheritDoc} */
    public void insertTurn(
            UUID controlTurnId,
            UUID conversationId,
            UUID sourceMessageId,
            String idempotencyKey) {
        mapper.insertTurn(
                controlTurnId,
                conversationId,
                sourceMessageId,
                idempotencyKey);
    }

    /** {@inheritDoc} */
    public void attachJob(UUID controlTurnId, UUID jobId) {
        if (mapper.attachJob(controlTurnId, jobId) != 1) {
            throw new RuntimeStateException(
                    "CONTROL_TURN_NOT_INITIALIZING",
                    "ControlTurn cannot attach Job: " + controlTurnId);
        }
    }

    /** {@inheritDoc} */
    public void insertDecision(
            UUID decisionId,
            UUID controlTurnId,
            UUID conversationId,
            UUID sourceMessageId,
            UUID jobId,
            IntentRecognition recognition,
            String constraintsJson,
            String taskGraphJson) {
        mapper.insertDecision(
                decisionId,
                controlTurnId,
                conversationId,
                sourceMessageId,
                jobId,
                recognition,
                constraintsJson,
                taskGraphJson);
    }

    /** {@inheritDoc} */
    public void completeTurn(UUID controlTurnId) {
        if (mapper.completeTurn(controlTurnId) != 1) {
            throw new RuntimeStateException(
                    "CONTROL_TURN_NOT_INITIALIZING",
                    "ControlTurn cannot complete: " + controlTurnId);
        }
    }

    /** 转换 ControlTurn 数据库行。 */
    private ControlTurn toTurn(Map<String, Object> row) {
        return new ControlTurn(
                uuid(row.get("id")),
                uuid(row.get("conversationId")),
                uuid(row.get("sourceMessageId")),
                text(row, "idempotencyKey"),
                ControlTurnStatus.valueOf(text(row, "status")),
                nullableUuid(row.get("jobId")),
                number(row, "version").longValue(),
                instant(row.get("createdAt")),
                instant(row.get("updatedAt")));
    }

    /** 转换 ControlDecision 数据库行。 */
    private ControlDecisionView toDecision(Map<String, Object> row) {
        return new ControlDecisionView(
                uuid(row.get("id")),
                uuid(row.get("controlTurnId")),
                uuid(row.get("sourceMessageId")),
                nullableUuid(row.get("jobId")),
                text(row, "intentType"),
                number(row, "confidence").doubleValue(),
                text(row, "classifier"),
                text(row, "goalSummary"),
                text(row, "decisionSummary"),
                constraints(text(row, "constraintsJson")),
                booleanValue(row.get("requiresClarification")),
                booleanValue(row.get("compoundTask")),
                text(row, "riskLevel"),
                instant(row.get("createdAt")));
    }

    /** 解析约束 JSON。 */
    private List<String> constraints(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to read control constraints",
                    exception);
        }
    }

    /** 读取必填文本。 */
    private String text(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }

    /** 读取必填 UUID。 */
    private UUID uuid(Object value) {
        return UUID.fromString(String.valueOf(value));
    }

    /** 读取可空 UUID。 */
    private UUID nullableUuid(Object value) {
        return value == null ? null : uuid(value);
    }

    /** 读取数值。 */
    private Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    /** 读取数据库布尔值。 */
    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return ((Number) value).intValue() != 0;
    }

    /** 读取 UTC 时间。 */
    private Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        }
        throw new IllegalArgumentException(
                "Unsupported timestamp value: " + value);
    }
}
