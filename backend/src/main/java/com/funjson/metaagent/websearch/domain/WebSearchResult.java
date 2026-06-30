package com.funjson.metaagent.websearch.domain;

import java.time.Instant;

/**
 * 外部搜索结果条目。
 *
 * @param title 标题
 * @param url 来源 URL
 * @param snippet 摘要片段
 * @param publishedAt 可选发布时间
 * @param provider 搜索 Provider
 * @param rank Provider 返回排序
 * @param sourceType 初步来源类型
 */
public record WebSearchResult(
        String title,
        String url,
        String snippet,
        Instant publishedAt,
        String provider,
        int rank,
        WebSourceType sourceType) {

    /**
     * 兼容旧测试和调用点的构造器。
     *
     * @param title 标题
     * @param url URL
     * @param snippet 摘要
     * @param publishedAt 发布时间
     */
    public WebSearchResult(
            String title,
            String url,
            String snippet,
            Instant publishedAt) {
        this(
                title,
                url,
                snippet,
                publishedAt,
                "unknown",
                0,
                WebSourceType.UNKNOWN);
    }

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
        provider = provider == null || provider.isBlank()
                ? "unknown"
                : provider.trim();
        rank = Math.max(0, rank);
        sourceType = sourceType == null ? WebSourceType.UNKNOWN : sourceType;
    }
}
