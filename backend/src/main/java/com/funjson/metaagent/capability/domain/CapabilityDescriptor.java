package com.funjson.metaagent.capability.domain;

import java.util.Map;

/**
 * CapabilityAdapter 产生的框架无关描述。
 *
 * @param ref 不可变来源引用
 * @param name 名称
 * @param adapterId Adapter ID
 * @param type Capability 类型
 * @param scopeType 作用域类型
 * @param parameters 类型化参数的原始结构
 */
public record CapabilityDescriptor(
        CapabilityRef ref,
        String name,
        String adapterId,
        CapabilityType type,
        CapabilityScopeType scopeType,
        Map<String, Object> parameters) {

    /**
     * 校验并复制描述。
     */
    public CapabilityDescriptor {
        if (ref == null) {
            throw new IllegalArgumentException(
                    "Capability reference is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Capability name is required");
        }
        if (adapterId == null || adapterId.isBlank()) {
            throw new IllegalArgumentException(
                    "Capability adapter is required");
        }
        if (type == null || scopeType == null) {
            throw new IllegalArgumentException(
                    "Capability type and scope are required");
        }
        parameters = parameters == null
                ? Map.of()
                : Map.copyOf(parameters);
    }
}
