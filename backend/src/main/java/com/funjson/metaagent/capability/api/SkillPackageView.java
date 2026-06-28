package com.funjson.metaagent.capability.api;

import com.funjson.metaagent.capability.domain.CapabilityType;

import java.util.List;

/**
 * 已导入 SkillPackage 视图。
 *
 * @param id Skill ID
 * @param version 版本
 * @param name 名称
 * @param capabilityType 编译类型
 * @param packageChecksum 整包 checksum
 * @param resourcePaths 资源路径
 * @param executableToolIds 注册的脚本 Tool ID
 */
public record SkillPackageView(
        String id,
        int version,
        String name,
        CapabilityType capabilityType,
        String packageChecksum,
        List<String> resourcePaths,
        List<String> executableToolIds) {
}
