package com.funjson.metaagent.tool.domain;

import java.util.List;

/**
 * 描述模型在 Loop 中可以选择的工具能力。
 *
 * @param name 稳定工具名
 * @param type 工具类型
 * @param description 面向模型的功能说明
 * @param inputSchemaJson 输入 Schema JSON
 * @param requiredPermissions 所需权限
 * @param dynamic 是否运行时动态加载
 */
public record ToolDefinition(
        String name,
        ToolType type,
        String description,
        String inputSchemaJson,
        List<String> requiredPermissions,
        boolean dynamic) {

    /**
     * 复制权限集合，保证工具定义不可变。
     */
    public ToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Tool type is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "Tool description is required");
        }
        inputSchemaJson = inputSchemaJson == null
                ? "{}"
                : inputSchemaJson;
        requiredPermissions = requiredPermissions == null
                ? List.of()
                : List.copyOf(requiredPermissions);
    }
}
