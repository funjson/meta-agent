package com.funjson.metaagent.provider.domain;

/**
 * 暴露给模型原生 function/tool calling 的工具 Schema。
 *
 * @param toolId 平台内部稳定 Tool ID，例如 web.search
 * @param functionName Provider 可接受的函数名，例如 web_search
 * @param description 面向模型的工具说明
 * @param inputSchemaJson JSON Schema 字符串
 */
public record ModelToolSpec(
        String toolId,
        String functionName,
        String description,
        String inputSchemaJson) {

    /**
     * 校验工具 Schema 的最小合同。
     */
    public ModelToolSpec {
        if (toolId == null || toolId.isBlank()) {
            throw new IllegalArgumentException("Tool id is required");
        }
        if (functionName == null || functionName.isBlank()) {
            throw new IllegalArgumentException("Function name is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Tool description is required");
        }
        inputSchemaJson = inputSchemaJson == null || inputSchemaJson.isBlank()
                ? "{\"type\":\"object\",\"properties\":{}}"
                : inputSchemaJson;
    }
}
