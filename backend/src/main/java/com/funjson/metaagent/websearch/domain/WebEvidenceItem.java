package com.funjson.metaagent.websearch.domain;

/**
 * Evidence snippet extracted from a source document.
 *
 * @param sourceUrl source URL
 * @param title source title
 * @param excerpt concise evidence excerpt
 * @param relevanceScore simple local relevance score
 * @param sourceType inferred source type
 */
public record WebEvidenceItem(
        String sourceUrl,
        String title,
        String excerpt,
        double relevanceScore,
        WebSourceType sourceType) {

    /**
     * Validates required citation fields.
     */
    public WebEvidenceItem {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("Evidence source URL required");
        }
        title = title == null ? "" : title.trim();
        excerpt = excerpt == null ? "" : excerpt.trim();
        relevanceScore = Math.max(0, Math.min(1, relevanceScore));
        sourceType = sourceType == null ? WebSourceType.UNKNOWN : sourceType;
    }
}
