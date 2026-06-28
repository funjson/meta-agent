package com.funjson.metaagent.provider.domain;

import java.util.UUID;

import com.funjson.metaagent.prompt.domain.RenderedPrompt;

/**
 * 描述一次与具体 AI SDK 无关的模型调用请求。
 *
 * @param taskRunId 关联 TaskRun，Control 预处理调用时可为空
 * @param loopNodeId 关联 LoopNode，Control 预处理调用时可为空
 * @param inputSummary 用于审计的非敏感输入摘要
 * @param prompt 已完成版本解析和变量渲染的 Prompt
 * @param maxTokens 最大输出 Token
 */
public record ModelRequest(
        UUID taskRunId,
        UUID loopNodeId,
        String inputSummary,
        RenderedPrompt prompt,
        int maxTokens) {
}
