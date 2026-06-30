package com.funjson.metaagent.websearch.domain;

import java.util.List;

/**
 * Source document and evidence snippets extracted from a single fetched URL.
 *
 * @param document fetched and cleaned source document
 * @param evidence query-relevant evidence snippets
 */
public record WebEvidenceExtraction(
        WebSourceDocument document,
        List<WebEvidenceItem> evidence) {

    /**
     * Normalizes the evidence list.
     */
    public WebEvidenceExtraction {
        if (document == null) {
            throw new IllegalArgumentException("Source document is required");
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
