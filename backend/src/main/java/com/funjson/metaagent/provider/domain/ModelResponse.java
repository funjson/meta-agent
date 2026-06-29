package com.funjson.metaagent.provider.domain;

import java.util.List;

/**
 * 框架无关的模型响应。
 *
 * @param provider Provider ID
 * @param model 模型名
 * @param content 最终内容
 * @param finishReason 结束原因
 * @param toolCalls 模型原生工具调用请求
 * @param reasoningContent Provider 返回的推理/思考内容；默认不直接展示给用户
 */
public record ModelResponse(
        String provider,
        String model,
        String content,
        String finishReason,
        List<ModelToolCall> toolCalls,
        String reasoningContent) {

    /**
     * 兼容旧调用点的默认构造器。
     */
    public ModelResponse(
            String provider,
            String model,
            String content,
            String finishReason) {
        this(provider, model, content, finishReason, List.of(), "");
    }

    /**
     * 复制工具调用集合。
     */
    public ModelResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        reasoningContent = reasoningContent == null ? "" : reasoningContent;
    }

    /**
     * @return 是否包含模型原生工具调用
     */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
