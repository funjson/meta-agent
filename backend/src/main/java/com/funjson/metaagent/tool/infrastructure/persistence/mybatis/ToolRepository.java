package com.funjson.metaagent.tool.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.tool.application.port.out.ToolStore;
import com.funjson.metaagent.tool.domain.ScriptToolSpec;
import com.funjson.metaagent.tool.domain.ToolDefinition;
import com.funjson.metaagent.tool.domain.ToolType;
import org.springframework.stereotype.Repository;

/**
 * ToolStore 的 MyBatis 适配器。
 */
@Repository
public class ToolRepository implements ToolStore {

    private final ToolPersistenceMapper mapper;

    /**
     * 创建 Tool Repository。
     *
     * @param mapper Tool Mapper
     */
    public ToolRepository(ToolPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    public List<ToolDefinition> findScriptToolDefinitions() {
        return mapper.findScriptTools().stream()
                .map(row -> new ToolDefinition(
                        text(row, "toolId"),
                        ToolType.FUNCTION,
                        "Skill script "
                                + text(row, "packageId")
                                + "@"
                                + number(row, "packageVersion"),
                        text(row, "argumentSchemaJson"),
                        List.of("tool:execute"),
                        true))
                .toList();
    }

    /** {@inheritDoc} */
    public Optional<ScriptToolSpec> findScriptTool(String toolId) {
        return Optional.ofNullable(mapper.findScriptTool(toolId))
                .map(this::toScriptTool);
    }

    /** {@inheritDoc} */
    public Optional<UUID> findConversationIdByJobId(UUID jobId) {
        return Optional.ofNullable(mapper.findConversationIdByJobId(jobId))
                .map(UUID::fromString);
    }

    /** {@inheritDoc} */
    public Optional<Map<String, Object>> findInvocationByIdempotencyKey(
            String idempotencyKey) {
        return Optional.ofNullable(
                mapper.findInvocationByIdempotencyKey(idempotencyKey));
    }

    /** {@inheritDoc} */
    public void insertInvocation(
            UUID invocationId,
            String toolId,
            String toolType,
            String idempotencyKey,
            String argumentsJson,
            UUID jobId,
            UUID taskId,
            UUID taskRunId,
            UUID loopRunId,
            UUID loopNodeId) {
        mapper.insertInvocation(
                invocationId,
                toolId,
                toolType,
                idempotencyKey,
                argumentsJson,
                jobId,
                taskId,
                taskRunId,
                loopRunId,
                loopNodeId);
    }

    /** {@inheritDoc} */
    public void markRunning(UUID invocationId) {
        mapper.markRunning(invocationId);
    }

    /** {@inheritDoc} */
    public void complete(UUID invocationId, String resultJson) {
        mapper.complete(invocationId, resultJson);
    }

    /** {@inheritDoc} */
    public void attachClarification(
            UUID invocationId,
            UUID clarificationRequestId) {
        mapper.attachClarification(invocationId, clarificationRequestId);
    }

    /** {@inheritDoc} */
    public void fail(
            UUID invocationId,
            String errorMessage,
            String resultJson) {
        mapper.fail(invocationId, errorMessage, resultJson);
    }

    /** 转换脚本工具行。 */
    private ScriptToolSpec toScriptTool(Map<String, Object> row) {
        return new ScriptToolSpec(
                text(row, "packageId"),
                number(row, "packageVersion").intValue(),
                text(row, "resourcePath"),
                text(row, "toolId"),
                text(row, "interpreter"),
                text(row, "argumentSchemaJson"),
                text(row, "sideEffectClass"),
                text(row, "contentText"));
    }

    /** 读取文本。 */
    private String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    /** 读取数值。 */
    private Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }
}
