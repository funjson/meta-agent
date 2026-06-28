package com.funjson.metaagent.context.domain;

/**
 * 可进入上下文工程的用户可见消息。
 *
 * @param role 消息角色
 * @param messageType 消息类型
 * @param content 消息内容
 */
public record ContextMessage(
        String role,
        String messageType,
        String content) {
}
