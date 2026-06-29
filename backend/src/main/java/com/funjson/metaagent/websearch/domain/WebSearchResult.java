package com.funjson.metaagent.websearch.domain;

import java.time.Instant;

/**
 * 外部搜索结果条目。
 *
 * @param title 标题
 * @param url 来源 URL
 * @param snippet 摘要片段
 * @param publishedAt 可选发布时间
 */
public record WebSearchResult(
        String title,
        String url,
        String snippet,
        Instant publishedAt) {

    /**
     * 校验搜索结果。
     */
    public WebSearchResult {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Search result title required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Search result url required");
        }
        snippet = snippet == null ? "" : snippet;
    }
}
