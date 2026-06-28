package com.funjson.metaagent.tool.domain;

/**
 * SkillPackage 中注册的脚本工具执行规格。
 *
 * @param packageId SkillPackage ID
 * @param packageVersion SkillPackage 版本
 * @param resourcePath 脚本路径
 * @param toolId Tool ID
 * @param interpreter 解释器 ID
 * @param argumentSchemaJson 参数 Schema
 * @param sideEffectClass 副作用分类
 * @param scriptContent 脚本文本
 */
public record ScriptToolSpec(
        String packageId,
        int packageVersion,
        String resourcePath,
        String toolId,
        String interpreter,
        String argumentSchemaJson,
        String sideEffectClass,
        String scriptContent) {
}
