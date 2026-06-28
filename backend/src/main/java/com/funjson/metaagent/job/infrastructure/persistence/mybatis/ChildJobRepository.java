package com.funjson.metaagent.job.infrastructure.persistence.mybatis;

import com.funjson.metaagent.job.application.port.out.ChildJobStore;
import com.funjson.metaagent.job.domain.ChildJobParentSnapshot;
import com.funjson.metaagent.job.domain.ChildJobCompletionSnapshot;
import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.runtime.domain.ChildJobRequest;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Child Job MyBatis 持久化适配器。
 */
@Repository
public class ChildJobRepository implements ChildJobStore {

    private final ChildJobPersistenceMapper mapper;

    /**
     * 创建 Child Job Repository。
     *
     * @param mapper MyBatis Mapper
     */
    public ChildJobRepository(ChildJobPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    public Optional<UUID> findChildJobId(String idempotencyKey) {
        return Optional.ofNullable(mapper.findChildJobId(idempotencyKey))
                .map(UUID::fromString);
    }

    /** {@inheritDoc} */
    public ChildJobParentSnapshot lockParent(UUID parentJobId) {
        Map<String, Object> row = mapper.lockParent(parentJobId);
        if (row == null) {
            throw new RuntimeStateException(
                    "PARENT_JOB_NOT_FOUND",
                    "Parent Job not found: " + parentJobId);
        }
        return new ChildJobParentSnapshot(
                uuid(row, "jobId"),
                uuid(row, "rootJobId"),
                number(row, "recursionDepth").intValue(),
                text(row, "agentProfileId"),
                nullableUuid(row.get("conversationId")),
                nullableUuid(row.get("sourceMessageId")),
                text(row, "providerId"),
                JobStatus.valueOf(text(row, "status")));
    }

    /** {@inheritDoc} */
    public long countDirectChildren(UUID parentJobId) {
        return mapper.countDirectChildren(parentJobId);
    }

    /** {@inheritDoc} */
    public long countTreeJobs(UUID rootJobId) {
        return mapper.countTreeJobs(rootJobId);
    }

    /** {@inheritDoc} */
    public void insertDerivation(
            UUID derivationId,
            ChildJobParentSnapshot parent,
            UUID childJobId,
            UUID originTaskRunId,
            UUID originLoopNodeId,
            ChildJobRequest request,
            String requestJson) {
        mapper.insertDerivation(
                derivationId,
                parent.jobId(),
                childJobId,
                originTaskRunId,
                originLoopNodeId,
                request.idempotencyKey(),
                request.sourceSkillId(),
                request.sourceSkillVersion(),
                requestJson);
    }

    /** {@inheritDoc} */
    public void bindOriginLoopNode(
            UUID originLoopNodeId,
            UUID childJobId) {
        if (mapper.bindOriginLoopNode(
                originLoopNodeId,
                childJobId) != 1) {
            throw new RuntimeStateException(
                    "ORIGIN_LOOP_NODE_NOT_WAITING",
                    "Origin LoopNode cannot bind Child Job");
        }
    }

    /** {@inheritDoc} */
    public Optional<ChildJobCompletionSnapshot> lockCompletion(
            UUID childJobId) {
        return Optional.ofNullable(mapper.lockCompletion(childJobId))
                .map(row -> new ChildJobCompletionSnapshot(
                        uuid(row, "derivationId"),
                        uuid(row, "parentJobId"),
                        uuid(row, "childJobId"),
                        uuid(row, "originTaskRunId"),
                        uuid(row, "originLoopNodeId"),
                        text(row, "derivationStatus"),
                        JobStatus.valueOf(text(row, "childJobStatus"))));
    }

    /** {@inheritDoc} */
    public String summarizeChildJob(UUID childJobId) {
        return mapper.summarizeChildJob(childJobId);
    }

    /** {@inheritDoc} */
    public int countChildJobEvidence(UUID childJobId) {
        return mapper.countChildJobEvidence(childJobId);
    }

    /** {@inheritDoc} */
    public boolean completeDerivation(
            UUID childJobId,
            String outcomeJson) {
        return mapper.completeDerivation(childJobId, outcomeJson) == 1;
    }

    /** {@inheritDoc} */
    public void clearOriginLoopNode(
            UUID originLoopNodeId,
            UUID childJobId) {
        mapper.clearOriginLoopNode(originLoopNodeId, childJobId);
    }

    /** 读取必填字符串。 */
    private String text(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }

    /** 读取数值。 */
    private Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    /** 读取必填 UUID。 */
    private UUID uuid(Map<String, Object> row, String key) {
        return UUID.fromString(text(row, key));
    }

    /** 读取可空 UUID。 */
    private UUID nullableUuid(Object value) {
        return value == null
                ? null
                : UUID.fromString(String.valueOf(value));
    }
}
