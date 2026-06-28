package com.funjson.metaagent.runtime.infrastructure.persistence.mybatis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.runtime.api.AuthorizationRequestView;
import com.funjson.metaagent.runtime.application.port.out.AuthorizationStore;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AuthorizationRequest MyBatis 持久化适配器。
 */
@Repository
public class AuthorizationRepository implements AuthorizationStore {

    private final AuthorizationPersistenceMapper mapper;
    private final ObjectMapper objectMapper;

    /** 创建授权 Repository。 */
    public AuthorizationRepository(
            AuthorizationPersistenceMapper mapper,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    public void insert(
            UUID id,
            UUID jobId,
            UUID taskRunId,
            UUID loopNodeId,
            String requestType,
            String sourceType,
            String sourceId,
            Map<String, Object> requestedDelta) {
        mapper.insert(
                id,
                jobId,
                taskRunId,
                loopNodeId,
                requestType,
                sourceType,
                sourceId,
                json(requestedDelta));
    }

    /** {@inheritDoc} */
    public List<AuthorizationRequestView> findByStatus(String status) {
        return mapper.findByStatus(status).stream()
                .map(this::map)
                .toList();
    }

    /** {@inheritDoc} */
    public void decide(
            UUID id,
            String expectedStatus,
            String targetStatus,
            Map<String, Object> decision) {
        if (mapper.decide(
                id,
                expectedStatus,
                targetStatus,
                json(decision)) != 1) {
            throw new RuntimeStateException(
                    "AUTHORIZATION_REQUEST_NOT_PENDING",
                    "Authorization request is not pending: " + id);
        }
    }

    /** 映射数据库行。 */
    private AuthorizationRequestView map(Map<String, Object> row) {
        return new AuthorizationRequestView(
                uuid(row.get("id")),
                uuid(row.get("jobId")),
                uuid(row.get("taskRunId")),
                uuid(row.get("loopNodeId")),
                text(row, "requestType"),
                text(row, "sourceType"),
                text(row, "sourceId"),
                mapJson(row.get("requestedDeltaJson")),
                text(row, "status"),
                mapJson(row.get("decisionJson")),
                instant(row.get("createdAt")),
                instant(row.get("decidedAt")));
    }

    /** 序列化 JSON。 */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize authorization request",
                    exception);
        }
    }

    /** 解析 JSON 对象。 */
    private Map<String, Object> mapJson(Object value) {
        if (value == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                    String.valueOf(value),
                    new TypeReference<>() {
                    });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to parse authorization request",
                    exception);
        }
    }

    /** 读取字符串。 */
    private String text(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }

    /** 读取可空 UUID。 */
    private UUID uuid(Object value) {
        return value == null
                ? null
                : UUID.fromString(String.valueOf(value));
    }

    /** 读取可空时间。 */
    private Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return ((java.time.LocalDateTime) value)
                .toInstant(java.time.ZoneOffset.UTC);
    }
}
