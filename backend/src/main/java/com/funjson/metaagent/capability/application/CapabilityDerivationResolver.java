package com.funjson.metaagent.capability.application;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.domain.CapabilityType;
import com.funjson.metaagent.loop.domain.ExecutionDerivationRequest;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.runtime.domain.CapabilityRequest;
import com.funjson.metaagent.runtime.domain.ChildJobRequest;
import com.funjson.metaagent.runtime.domain.ContractContribution;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.runtime.domain.SubagentProfileRef;
import com.funjson.metaagent.runtime.domain.TaskGraphTemplateRef;
import org.springframework.stereotype.Service;

/**
 * 从 STEP / CHILD_JOB Capability 中解析正式派生请求。
 */
@Service
public class CapabilityDerivationResolver {

    private final ObjectMapper objectMapper;

    /**
     * 创建 Capability Derivation Resolver。
     *
     * @param objectMapper JSON Mapper
     */
    public CapabilityDerivationResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从步骤型或 Child Job Capability 形成正式派生请求。
     *
     * @param context LoopNode 上下文
     * @param loads 当前节点生效的 Load
     * @return 派生请求；无派生时为空
     */
    public ExecutionDerivationRequest resolve(
            RunExecutionContext context,
            List<Map<String, Object>> loads) {
        for (Map<String, Object> load : loads) {
            CapabilityType type = CapabilityType.valueOf(
                    text(load, "capabilityType"));
            Map<String, Object> parameters = parameters(load);
            String loadId = text(load, "loadId");
            if (type == CapabilityType.STEP) {
                return ExecutionDerivationRequest.childLoop(
                        "capability:" + loadId + ":child",
                        "Step capability "
                                + text(load, "sourceId"),
                        requiredText(parameters, "childGoal"),
                        optionalText(parameters, "feedback"));
            }
            if (type == CapabilityType.CHILD_JOB) {
                return childJobRequest(context, load, parameters, loadId);
            }
        }
        return null;
    }

    /**
     * 解析 Child Job 派生请求。
     */
    private ExecutionDerivationRequest childJobRequest(
            RunExecutionContext context,
            Map<String, Object> load,
            Map<String, Object> parameters,
            String loadId) {
        Object rawRequest = parameters.get("childJob");
        if (rawRequest == null) {
            throw new RuntimeStateException(
                    "INVALID_CHILD_JOB_CAPABILITY",
                    "Child Job capability has no request");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> childJob =
                objectMapper.convertValue(rawRequest, Map.class);
        TaskGraphTemplateRef templateRef =
                childJob.get("templateRef") == null
                        ? null
                        : objectMapper.convertValue(
                                childJob.get("templateRef"),
                                TaskGraphTemplateRef.class);
        SubagentProfileRef subagentProfileRef =
                childJob.get("subagentProfileRef") == null
                        ? null
                        : objectMapper.convertValue(
                                childJob.get("subagentProfileRef"),
                                SubagentProfileRef.class);
        ContractContribution contractContribution =
                childJob.get("contractContribution") == null
                        ? ContractContribution.empty()
                        : objectMapper.convertValue(
                                childJob.get("contractContribution"),
                                ContractContribution.class);
        CapabilityRequest capabilityRequest =
                childJob.get("capabilityRequest") == null
                        ? CapabilityRequest.none()
                        : objectMapper.convertValue(
                                childJob.get("capabilityRequest"),
                                CapabilityRequest.class);
        ChildJobRequest request = new ChildJobRequest(
                requiredText(childJob, "goal"),
                stringList(childJob.get("constraints")),
                templateRef,
                subagentProfileRef,
                optionalText(
                        childJob,
                        "dynamicPlanningInstruction"),
                contractContribution,
                capabilityRequest,
                "capability:" + loadId + ":child-job",
                text(load, "sourceId"),
                ((Number) load.get("sourceVersion")).intValue());
        return ExecutionDerivationRequest.childJob(
                "Child Job capability "
                        + text(load, "sourceId")
                        + " loaded by LoopNode "
                        + context.loopNodeId(),
                request);
    }

    /** 解析 Load 描述参数。 */
    private Map<String, Object> parameters(Map<String, Object> load) {
        try {
            return objectMapper.readValue(
                    text(load, "descriptorJson"),
                    new TypeReference<>() {
                    });
        } catch (JsonProcessingException exception) {
            throw new RuntimeStateException(
                    "INVALID_CAPABILITY_DESCRIPTOR",
                    "Capability descriptor cannot be parsed");
        }
    }

    /** 读取必填参数。 */
    private String requiredText(
            Map<String, Object> parameters,
            String key) {
        String value = optionalText(parameters, key);
        if (value.isBlank()) {
            throw new RuntimeStateException(
                    "INVALID_CAPABILITY_DESCRIPTOR",
                    "Capability parameter is required: " + key);
        }
        return value;
    }

    /** 读取可选参数。 */
    private String optionalText(
            Map<String, Object> parameters,
            String key) {
        Object value = parameters.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 把可选 JSON 数组转换为稳定字符串列表。
     *
     * @param value 原始数组
     * @return 字符串列表
     */
    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(String::valueOf)
                .filter(item -> !item.isBlank())
                .toList();
    }

    /** 读取 Load 字段。 */
    private String text(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }
}
