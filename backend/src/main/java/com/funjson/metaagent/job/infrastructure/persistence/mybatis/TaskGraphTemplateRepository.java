package com.funjson.metaagent.job.infrastructure.persistence.mybatis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.job.api.TaskGraphTemplateView;
import com.funjson.metaagent.job.application.port.out.TaskGraphTemplateStore;
import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.job.domain.TaskGraphTemplateStatus;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * TaskGraphTemplate MyBatis 持久化适配器。
 */
@Repository
public class TaskGraphTemplateRepository
        implements TaskGraphTemplateStore {

    private final TaskGraphTemplatePersistenceMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建模板 Repository。
     *
     * @param mapper MyBatis Mapper
     * @param objectMapper JSON 序列化器
     */
    public TaskGraphTemplateRepository(
            TaskGraphTemplatePersistenceMapper mapper,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    public Optional<UUID> findTemplateId(
            String agentProfileId,
            String templateKey) {
        return Optional.ofNullable(
                        mapper.findTemplateId(
                                agentProfileId,
                                templateKey))
                .map(UUID::fromString);
    }

    /** {@inheritDoc} */
    public int nextVersion(
            String agentProfileId,
            String templateKey) {
        return mapper.nextVersion(agentProfileId, templateKey);
    }

    /** {@inheritDoc} */
    public void retireActiveVersions(
            String agentProfileId,
            String templateKey) {
        mapper.retireActiveVersions(agentProfileId, templateKey);
    }

    /** {@inheritDoc} */
    public void insert(
            UUID id,
            String agentProfileId,
            String templateKey,
            int version,
            String name,
            List<String> intentLabels,
            TaskGraphPlan graph,
            String checksum) {
        mapper.insert(
                id,
                agentProfileId,
                templateKey,
                version,
                name,
                json(intentLabels),
                json(graph),
                checksum);
    }

    /** {@inheritDoc} */
    public Optional<TaskGraphTemplateView> find(
            UUID id,
            int version) {
        return Optional.ofNullable(mapper.find(id, version))
                .map(this::map);
    }

    /** {@inheritDoc} */
    public List<TaskGraphTemplateView> findAll(
            String agentProfileId) {
        return mapper.findAll(agentProfileId).stream()
                .map(this::map)
                .toList();
    }

    /** {@inheritDoc} */
    public List<TaskGraphTemplateView> findActive(
            String agentProfileId) {
        return mapper.findActive(agentProfileId).stream()
                .map(this::map)
                .toList();
    }

    /** 映射数据库行。 */
    private TaskGraphTemplateView map(Map<String, Object> row) {
        try {
            List<String> labels = objectMapper.readValue(
                    String.valueOf(row.get("intentLabelsJson")),
                    new TypeReference<>() {
                    });
            TaskGraphPlan graph = objectMapper.readValue(
                    String.valueOf(row.get("graphJson")),
                    TaskGraphPlan.class);
            return new TaskGraphTemplateView(
                    UUID.fromString(String.valueOf(row.get("id"))),
                    String.valueOf(row.get("agentProfileId")),
                    String.valueOf(row.get("templateKey")),
                    ((Number) row.get("version")).intValue(),
                    String.valueOf(row.get("name")),
                    labels,
                    graph,
                    String.valueOf(row.get("checksum")),
                    TaskGraphTemplateStatus.valueOf(
                            String.valueOf(row.get("status"))),
                    instant(row.get("createdAt")));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to map task graph template",
                    exception);
        }
    }

    /** 序列化模板字段。 */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize task graph template",
                    exception);
        }
    }

    /**
     * 将 MyBatis 时间值转换为 Instant。
     *
     * @param value 数据库时间
     * @return Instant
     */
    private Instant instant(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        }
        throw new IllegalArgumentException(
                "Unsupported timestamp value: " + value);
    }
}
