package com.funjson.metaagent.capability.domain;

import java.util.List;

/**
 * 包含 SKILL.md 与辅助资源的不可变 SkillPackage。
 *
 * @param ref Skill ID 与版本
 * @param name 名称
 * @param skillMarkdown SKILL.md 原文
 * @param resources scripts/references/assets
 * @param compiledManifest 编译产物
 * @param packageChecksum 整包 SHA-256
 */
public record SkillPackage(
        CapabilityRef ref,
        String name,
        String skillMarkdown,
        List<SkillResource> resources,
        CompiledSkillManifest compiledManifest,
        String packageChecksum) {

    /**
     * 复制资源并校验包元数据。
     */
    public SkillPackage {
        if (ref == null || name == null || name.isBlank()
                || skillMarkdown == null || skillMarkdown.isBlank()
                || compiledManifest == null
                || packageChecksum == null
                || packageChecksum.isBlank()) {
            throw new IllegalArgumentException(
                    "SkillPackage metadata is required");
        }
        resources = resources == null
                ? List.of()
                : List.copyOf(resources);
        long uniquePaths = resources.stream()
                .map(SkillResource::path)
                .distinct()
                .count();
        if (uniquePaths != resources.size()) {
            throw new IllegalArgumentException(
                    "SkillPackage contains duplicate resource paths");
        }
    }
}
