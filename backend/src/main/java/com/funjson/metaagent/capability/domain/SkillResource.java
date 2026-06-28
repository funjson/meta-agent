package com.funjson.metaagent.capability.domain;

import java.util.List;

/**
 * SkillPackage 中的不可变资源。
 *
 * @param path 包内相对路径
 * @param type 资源类型
 * @param content 文本内容
 * @param contentHash 内容 SHA-256
 * @param executable 可选脚本 executable
 */
public record SkillResource(
        String path,
        SkillResourceType type,
        String content,
        String contentHash,
        SkillExecutable executable) {

    /**
     * 校验路径不能逃逸 SkillPackage。
     */
    public SkillResource {
        String normalized = path == null
                ? ""
                : path.replace('\\', '/');
        List<String> segments = List.of(normalized.split("/"));
        if (normalized.isBlank()
                || normalized.startsWith("/")
                || normalized.matches("^[A-Za-z]:.*")
                || segments.contains("..")
                || segments.contains(".")) {
            throw new IllegalArgumentException(
                    "Skill resource path must stay inside the package");
        }
        path = normalized;
        if (type == null || contentHash == null
                || contentHash.isBlank()) {
            throw new IllegalArgumentException(
                    "Skill resource type and checksum are required");
        }
        content = content == null ? "" : content;
        if (type == SkillResourceType.SCRIPT && executable == null) {
            throw new IllegalArgumentException(
                    "Script resource requires executable metadata");
        }
        if (executable != null
                && !path.equals(executable.entrypoint())) {
            throw new IllegalArgumentException(
                    "Executable entrypoint must equal resource path");
        }
    }
}
