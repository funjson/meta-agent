package com.funjson.metaagent.capability.domain;

/**
 * 持久化的不可变 CapabilitySource。
 *
 * @param ref 来源引用
 * @param name 名称
 * @param sourceType 来源类型
 * @param adapterId Adapter ID
 * @param capabilityType Capability 类型
 * @param scopeType 作用域
 * @param descriptorJson 原始描述 JSON
 * @param checksum 内容校验和
 */
public record CapabilitySource(
        CapabilityRef ref,
        String name,
        String sourceType,
        String adapterId,
        CapabilityType capabilityType,
        CapabilityScopeType scopeType,
        String descriptorJson,
        String checksum) {
}
