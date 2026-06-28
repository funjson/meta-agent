package com.funjson.metaagent.capability.domain;

import java.util.Map;

/**
 * Skill 脚本注册到 Tool Runtime 的可执行描述。
 *
 * @param toolId 稳定 Tool ID
 * @param interpreter 允许的解释器 ID
 * @param entrypoint SkillPackage 内入口路径
 * @param argumentSchema 参数 JSON Schema
 * @param sideEffectClass 副作用分类
 */
public record SkillExecutable(
        String toolId,
        String interpreter,
        String entrypoint,
        Map<String, Object> argumentSchema,
        String sideEffectClass) {

    /**
     * 校验可执行描述。
     */
    public SkillExecutable {
        if (toolId == null || toolId.isBlank()
                || interpreter == null || interpreter.isBlank()
                || entrypoint == null || entrypoint.isBlank()) {
            throw new IllegalArgumentException(
                    "Skill executable identity is required");
        }
        argumentSchema = argumentSchema == null
                ? Map.of()
                : Map.copyOf(argumentSchema);
        sideEffectClass = sideEffectClass == null
                ? "UNKNOWN"
                : sideEffectClass;
    }
}
