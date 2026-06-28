package com.funjson.metaagent.provider.domain;

/**
 * 框架无关的模型响应。
 *
 * @param provider Provider ID
 * @param model 模型名
 * @param content 最终内容
 * @param finishReason 结束原因
 */
public record ModelResponse(
        String provider,
        String model,
        String content,
        String finishReason) {
}
