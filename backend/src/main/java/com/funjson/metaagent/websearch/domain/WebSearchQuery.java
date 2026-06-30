package com.funjson.metaagent.websearch.domain;

import java.util.List;

/**
 * Structured web search request used by search provider adapters.
 *
 * @param query natural language or keyword query
 * @param limit maximum result count
 * @param recencyDays optional freshness window in days
 * @param domains optional allowlist domains preferred by the query
 * @param locale optional locale hint such as {@code zh-CN}
 */
public record WebSearchQuery(
        String query,
        int limit,
        Integer recencyDays,
        List<String> domains,
        String locale) {

    /**
     * Validates and normalizes the query.
     */
    public WebSearchQuery {
        query = query == null ? "" : query.replaceAll("\\s+", " ").trim();
        if (query.isBlank()) {
            throw new IllegalArgumentException("Search query is required");
        }
        limit = Math.max(1, limit);
        recencyDays = recencyDays == null || recencyDays <= 0
                ? null
                : recencyDays;
        domains = domains == null
                ? List.of()
                : domains.stream()
                        .filter(domain -> domain != null
                                && !domain.isBlank())
                        .map(domain -> domain
                                .replaceFirst("^https?://", "")
                                .replaceFirst("/.*$", "")
                                .trim()
                                .toLowerCase())
                        .distinct()
                        .toList();
        locale = locale == null ? "" : locale.trim();
    }
}
