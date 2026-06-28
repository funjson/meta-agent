package com.funjson.metaagent.capability.domain;

/**
 * 不可变 CapabilitySource 版本引用。
 *
 * @param id Capability ID
 * @param version 版本
 */
public record CapabilityRef(
        String id,
        int version) {

    /**
     * 校验引用。
     */
    public CapabilityRef {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Capability id is required");
        }
        if (version < 1) {
            throw new IllegalArgumentException(
                    "Capability version must be positive");
        }
    }
}
