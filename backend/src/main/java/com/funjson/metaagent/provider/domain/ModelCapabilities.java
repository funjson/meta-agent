package com.funjson.metaagent.provider.domain;

/**
 * 框架看到的模型能力快照。
 *
 * @param toolCalling 是否支持原生 function/tool calling
 * @param reasoning 是否属于推理/思考模型
 * @param reasoningContent 是否可能返回推理内容字段
 * @param thinkingMode 是否支持显式 thinking 开关
 * @param vision 是否支持视觉输入
 */
public record ModelCapabilities(
        boolean toolCalling,
        boolean reasoning,
        boolean reasoningContent,
        boolean thinkingMode,
        boolean vision) {
}
