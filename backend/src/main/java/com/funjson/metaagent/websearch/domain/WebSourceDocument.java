package com.funjson.metaagent.websearch.domain;

import java.time.Instant;

/**
 * Cleaned and metadata-rich source document that can be cited by a response.
 *
 * @param url canonical or final URL
 * @param title source title
 * @param description optional page description
 * @param sourceType inferred source type
 * @param contentType original content type
 * @param fetchedAt fetch timestamp
 * @param contentHash hash of cleaned text for dedupe and audit
 * @param text cleaned readable text
 */
public record WebSourceDocument(
        String url,
        String title,
        String description,
        WebSourceType sourceType,
        String contentType,
        Instant fetchedAt,
        String contentHash,
        String text) {

    /**
     * Validates source identity and normalizes optional fields.
     */
    public WebSourceDocument {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Source URL is required");
        }
        title = title == null || title.isBlank() ? url : title.trim();
        description = description == null ? "" : description.trim();
        sourceType = sourceType == null ? WebSourceType.UNKNOWN : sourceType;
        contentType = contentType == null ? "" : contentType.trim();
        fetchedAt = fetchedAt == null ? Instant.now() : fetchedAt;
        contentHash = contentHash == null ? "" : contentHash.trim();
        text = text == null ? "" : text.trim();
    }
}
