package com.funjson.metaagent.intent.domain;

import java.util.UUID;

/**
 * 描述意图识别所需的对话上下文。
 *
 * @param userMessage 当前用户消息
 * @param conversationContext 可公开给分类模型的精简上下文
 * @param activeJobId 当前活跃 Job
 * @param modelClassificationAllowed 当前请求是否允许调用真实模型分类
 */
public record IntentRecognitionRequest(
        String userMessage,
        String conversationContext,
        UUID activeJobId,
        boolean modelClassificationAllowed) {
}
