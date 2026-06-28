package com.funjson.metaagent.clarification.infrastructure.persistence.mybatis;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.clarification.application.port.out.ClarificationStore;
import com.funjson.metaagent.clarification.domain.ClarificationReasonType;
import com.funjson.metaagent.clarification.domain.ClarificationRequest;
import com.funjson.metaagent.clarification.domain.ClarificationRequestDraft;
import com.funjson.metaagent.clarification.domain.ClarificationSourceType;
import com.funjson.metaagent.clarification.domain.ClarificationStatus;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.stereotype.Repository;

/**
 * ClarificationStore 的 MyBatis 适配器。
 */
@Repository
public class ClarificationRepository implements ClarificationStore {

    private final ClarificationPersistenceMapper mapper;

    /**
     * 创建澄清 Repository。
     *
     * @param mapper MyBatis Mapper
     */
    public ClarificationRepository(
            ClarificationPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    public Optional<ClarificationRequest> findOpenByJobId(UUID jobId) {
        return Optional.ofNullable(mapper.findOpenByJobId(jobId))
                .map(this::map);
    }

    /** {@inheritDoc} */
    public List<ClarificationRequest> findOpenByConversationId(
            UUID conversationId) {
        return mapper.findOpenByConversationId(conversationId).stream()
                .map(this::map)
                .toList();
    }

    /** {@inheritDoc} */
    public List<ClarificationRequest> findRecentResolvedByConversationId(
            UUID conversationId,
            int limit) {
        return mapper.findRecentResolvedByConversationId(
                        conversationId,
                        limit)
                .stream()
                .map(this::map)
                .toList();
    }

    /** {@inheritDoc} */
    public List<ClarificationRequest> findOpenBySource(
            UUID jobId,
            UUID taskId,
            UUID taskRunId,
            UUID loopNodeId) {
        return mapper.findOpenBySource(
                        jobId,
                        taskId,
                        taskRunId,
                        loopNodeId)
                .stream()
                .map(this::map)
                .toList();
    }

    /** {@inheritDoc} */
    public void insert(
            UUID id,
            UUID conversationId,
            UUID jobId,
            UUID taskId,
            UUID taskRunId,
            UUID loopNodeId,
            ClarificationRequestDraft draft) {
        mapper.insert(
                id,
                conversationId,
                jobId,
                taskId,
                taskRunId,
                loopNodeId,
                draft);
    }

    /** {@inheritDoc} */
    public void answer(
            UUID clarificationRequestId,
            UUID answerMessageId,
            String answer) {
        if (mapper.answer(
                clarificationRequestId,
                answerMessageId,
                answer) != 1) {
            throw new RuntimeStateException(
                    "CLARIFICATION_NOT_OPEN",
                    "Clarification cannot accept answer: "
                            + clarificationRequestId);
        }
    }

    /** {@inheritDoc} */
    public void recordPartialAnswer(
            UUID clarificationRequestId,
            UUID answerMessageId,
            String answer,
            String partialResolutionJson) {
        if (mapper.recordPartialAnswer(
                clarificationRequestId,
                answerMessageId,
                answer,
                partialResolutionJson) != 1) {
            throw new RuntimeStateException(
                    "CLARIFICATION_NOT_OPEN",
                    "Clarification cannot record partial answer: "
                            + clarificationRequestId);
        }
    }

    /** {@inheritDoc} */
    public void resolve(
            UUID clarificationRequestId,
            String resolutionJson) {
        if (mapper.resolve(
                clarificationRequestId,
                resolutionJson) != 1) {
            throw new RuntimeStateException(
                    "CLARIFICATION_NOT_ANSWERED",
                    "Clarification cannot be resolved: "
                            + clarificationRequestId);
        }
    }

    /**
     * 映射数据库行。
     *
     * @param row 数据库行
     * @return 澄清请求
     */
    private ClarificationRequest map(Map<String, Object> row) {
        return new ClarificationRequest(
                uuid(row.get("id")),
                uuid(row.get("conversationId")),
                nullableUuid(row.get("jobId")),
                nullableUuid(row.get("taskId")),
                nullableUuid(row.get("taskRunId")),
                nullableUuid(row.get("loopNodeId")),
                ClarificationSourceType.valueOf(text(row, "sourceType")),
                ClarificationReasonType.valueOf(text(row, "reasonType")),
                ClarificationStatus.valueOf(text(row, "status")),
                text(row, "question"),
                nullableText(row.get("contractJson")),
                nullableText(row.get("answer")),
                nullableUuid(row.get("answerMessageId")),
                nullableText(row.get("resolutionJson")),
                nullableInstant(row.get("resolvedAt")),
                text(row, "blockingSummary"),
                number(row, "maxRounds").intValue(),
                number(row, "currentRound").intValue(),
                instant(row.get("createdAt")),
                instant(row.get("updatedAt")));
    }

    /** 读取必填文本。 */
    private String text(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }

    /** 读取可空文本。 */
    private String nullableText(Object value) {
        return value == null ? null : String.valueOf(value);
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

    /** 转换时间。 */
    private Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.atZone(java.time.ZoneOffset.UTC)
                    .toInstant();
        }
        throw new IllegalArgumentException(
                "Unsupported timestamp value: " + value);
    }

    /** 转换可空时间。 */
    private Instant nullableInstant(Object value) {
        return value == null ? null : instant(value);
    }
}
