package com.funjson.metaagent.runtime.infrastructure.persistence.mybatis;

import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.loop.infrastructure.persistence.mybatis.RuntimePersistenceMapper;
import com.funjson.metaagent.runtime.application.port.out.TaskIntentScopeStore;
import com.funjson.metaagent.runtime.domain.TaskIntentScope;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-backed reader for Job task intent scopes.
 */
@Repository
public class TaskIntentScopeRepository implements TaskIntentScopeStore {

    private final RuntimePersistenceMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * Creates the repository.
     *
     * @param mapper runtime persistence mapper
     * @param objectMapper JSON mapper
     */
    public TaskIntentScopeRepository(
            RuntimePersistenceMapper mapper,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * Finds and parses the Job-scoped intent snapshot.
     */
    @Override
    public Optional<TaskIntentScope> findByJobId(UUID jobId) {
        String snapshot = mapper.findJobPolicySnapshot(jobId);
        if (snapshot == null || snapshot.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(snapshot);
            JsonNode scope = root.path("intentScope");
            if (!scope.isObject()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.treeToValue(
                    scope,
                    TaskIntentScope.class));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }
}
