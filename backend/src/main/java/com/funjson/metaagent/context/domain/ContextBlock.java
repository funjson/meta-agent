package com.funjson.metaagent.context.domain;

/**
 * 一个可审计的 Loop 上下文片段。
 *
 * @param type 上下文类型
 * @param title 标题
 * @param content 内容
 * @param tokenEstimate 粗略 Token 估算
 */
public record ContextBlock(
        ContextBlockType type,
        String title,
        String content,
        int tokenEstimate) {

    /**
     * 校验上下文块。
     */
    public ContextBlock {
        if (type == null) {
            throw new IllegalArgumentException("Context block type required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Context block title required");
        }
        content = content == null ? "" : content;
        tokenEstimate = Math.max(tokenEstimate, 0);
    }
}
