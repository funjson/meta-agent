package com.funjson.metaagent.capability.api;

import com.funjson.metaagent.capability.domain.SkillExecutable;
import com.funjson.metaagent.capability.domain.SkillResourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * SkillPackage 导入请求。
 *
 * @param id Skill ID
 * @param version Skill 版本
 * @param name 名称
 * @param skillMarkdown SKILL.md 原文
 * @param resources 辅助资源
 */
public record ImportSkillPackageRequest(
        @NotBlank @Size(max = 100) String id,
        @Min(1) int version,
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Size(max = 200_000) String skillMarkdown,
        List<@Valid ResourceRequest> resources) {

    /**
     * 单个 Skill 资源请求。
     *
     * @param path 包内相对路径
     * @param type 类型
     * @param content 内容
     * @param executable 脚本 executable
     */
    public record ResourceRequest(
            @NotBlank @Size(max = 500) String path,
            @NotNull SkillResourceType type,
            @Size(max = 1_000_000) String content,
            SkillExecutable executable) {
    }
}
