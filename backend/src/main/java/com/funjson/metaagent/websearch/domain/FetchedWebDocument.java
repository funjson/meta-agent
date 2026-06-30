package com.funjson.metaagent.websearch.domain;

import java.time.Instant;

/**
 * Raw document fetched from an external URL before readability extraction.
 *
 * @param requestedUrl original requested URL
 * @param finalUrl final URL after redirects when known
 * @param contentType response content type
 * @param body response body decoded as text
 * @param fetchedAt fetch timestamp
 */
public record FetchedWebDocument(
        String requestedUrl,
        String finalUrl,
        String contentType,
        String body,
        Instant fetchedAt) {

    /**
     * Normalizes optional text fields.
     */
    public FetchedWebDocument {
        requestedUrl = requestedUrl == null ? "" : requestedUrl.trim();
        finalUrl = finalUrl == null || finalUrl.isBlank()
                ? requestedUrl
                : finalUrl.trim();
        contentType = contentType == null ? "" : contentType.trim();
        body = body == null ? "" : body;
        fetchedAt = fetchedAt == null ? Instant.now() : fetchedAt;
    }
}
