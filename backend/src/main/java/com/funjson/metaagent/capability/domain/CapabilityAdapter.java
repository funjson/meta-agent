package com.funjson.metaagent.capability.domain;

/**
 * 将不同来源的 Skill 描述转换为统一 CapabilityDescriptor。
 */
public interface CapabilityAdapter {

    /**
     * 返回稳定 Adapter ID。
     *
     * @return Adapter ID
     */
    String id();

    /**
     * 解析持久化来源。
     *
     * @param source 来源记录
     * @return Capability 描述
     */
    CapabilityDescriptor parse(CapabilitySource source);
}
