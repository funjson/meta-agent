package com.funjson.metaagent.capability.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.capability.domain.CapabilityType;
import com.funjson.metaagent.capability.domain.ScopedCapabilityContext;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.stereotype.Service;

/**
 * 解析当前 LoopNode 生效的 Capability 作用域。
 */
@Service
public class CapabilityScopeResolver {

    private final ObjectMapper objectMapper;

    /**
     * 创建 Capability Scope Resolver。
     *
     * @param objectMapper JSON Mapper
     */
    public CapabilityScopeResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 合并所有规范型 Capability。
     *
     * @param loads 当前节点生效的 Load
     * @return 局部上下文
     */
    public ScopedCapabilityContext resolve(
            List<Map<String, Object>> loads) {
        List<String> instructions = new ArrayList<>();
        Map<String, Object> policy = new LinkedHashMap<>();
        List<CapabilityRef> refs = new ArrayList<>();
        for (Map<String, Object> load : loads) {
            if (CapabilityType.valueOf(text(
                    load,
                    "capabilityType")) != CapabilityType.POLICY) {
                continue;
            }
            Map<String, Object> parameters = parameters(load);
            Object rawInstructions = parameters.get("instructions");
            if (rawInstructions instanceof List<?> values) {
                values.stream()
                        .map(String::valueOf)
                        .filter(value -> !value.isBlank())
                        .forEach(instructions::add);
            }
            Object rawPolicy = parameters.get("policy");
            if (rawPolicy instanceof Map<?, ?> values) {
                values.forEach((key, value) ->
                        policy.put(String.valueOf(key), value));
            }
            refs.add(ref(load));
        }
        return new ScopedCapabilityContext(
                instructions,
                policy,
                refs);
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

    /** 创建来源引用。 */
    private CapabilityRef ref(Map<String, Object> load) {
        return new CapabilityRef(
                text(load, "sourceId"),
                ((Number) load.get("sourceVersion")).intValue());
    }

    /** 读取 Load 字段。 */
    private String text(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }
}
