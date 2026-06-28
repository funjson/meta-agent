package com.funjson.metaagent.prompt.domain;

/**
 * 表示一次已经完成变量替换、可以发送给模型的 Prompt。
 *
 * @param promptId 稳定 Prompt 标识
 * @param version Prompt 版本
 * @param systemMessage system 消息
 * @param userMessage user 消息
 * @param contentHash system 与 user 内容的 SHA-256
 */
public record RenderedPrompt(
        String promptId,
        String version,
        String systemMessage,
        String userMessage,
        String contentHash) {
}
