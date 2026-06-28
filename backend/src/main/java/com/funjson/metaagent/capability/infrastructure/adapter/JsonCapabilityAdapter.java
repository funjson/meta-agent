package com.funjson.metaagent.capability.infrastructure.adapter;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.domain.CapabilityAdapter;
import com.funjson.metaagent.capability.domain.CapabilityDescriptor;
import com.funjson.metaagent.capability.domain.CapabilitySource;
import org.springframework.stereotype.Component;

/**
 * 解析数据库或配置中心保存的版本化 JSON Capability。
 */
@Component
public class JsonCapabilityAdapter implements CapabilityAdapter {

    public static final String ADAPTER_ID = "json-capability-v1";

    private final ObjectMapper objectMapper;

    /**
     * 创建 JSON Capability Adapter。
     *
     * @param objectMapper JSON 解析器
     */
    public JsonCapabilityAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 返回稳定 Adapter ID。
     *
     * @return Adapter ID
     */
    @Override
    public String id() {
        return ADAPTER_ID;
    }

    /**
     * 解析 Capability 描述 JSON。
     *
     * @param source 来源记录
     * @return 统一描述
     */
    @Override
    public CapabilityDescriptor parse(CapabilitySource source) {
        try {
            Map<String, Object> parameters = objectMapper.readValue(
                    source.descriptorJson(),
                    new TypeReference<>() {
                    });
            return new CapabilityDescriptor(
                    source.ref(),
                    source.name(),
                    id(),
                    source.capabilityType(),
                    source.scopeType(),
                    parameters);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid capability descriptor: "
                            + source.ref().id()
                            + "@"
                            + source.ref().version(),
                    exception);
        }
    }
}
