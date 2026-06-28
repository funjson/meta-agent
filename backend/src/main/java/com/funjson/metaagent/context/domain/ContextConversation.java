package com.funjson.metaagent.context.domain;

import java.util.UUID;

/**
 * ContextEnvelope 内部使用的 Conversation 摘要。
 *
 * @param id Conversation ID
 * @param title 标题
 * @param activeJobId 当前活跃 Job
 */
public record ContextConversation(
        UUID id,
        String title,
        UUID activeJobId) {
}
