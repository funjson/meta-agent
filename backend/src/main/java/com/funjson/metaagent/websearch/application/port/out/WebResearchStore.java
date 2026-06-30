package com.funjson.metaagent.websearch.application.port.out;

import java.util.UUID;

import com.funjson.metaagent.websearch.domain.WebEvidenceItem;
import com.funjson.metaagent.websearch.domain.WebResearchContext;
import com.funjson.metaagent.websearch.domain.WebSearchQuery;
import com.funjson.metaagent.websearch.domain.WebSearchResult;
import com.funjson.metaagent.websearch.domain.WebSourceDocument;

/**
 * Persistence port for auditable web research artifacts.
 */
public interface WebResearchStore {

    /**
     * Persists one search run initiated by a web.search tool call.
     *
     * @param searchRunId stable search run ID
     * @param context execution context that produced the search
     * @param query structured search query
     * @param resultCount number of returned candidates
     */
    void insertSearchRun(
            UUID searchRunId,
            WebResearchContext context,
            WebSearchQuery query,
            int resultCount);

    /**
     * Persists one candidate returned by a search provider.
     *
     * @param candidateId stable candidate ID
     * @param searchRunId owning search run ID
     * @param context execution context that produced the search
     * @param result search provider result
     */
    void insertSearchCandidate(
            UUID candidateId,
            UUID searchRunId,
            WebResearchContext context,
            WebSearchResult result);

    /**
     * Persists a fetched source document.
     *
     * @param sourceDocumentId stable source document ID
     * @param context execution context that produced the source
     * @param document source document
     */
    void insertSourceDocument(
            UUID sourceDocumentId,
            WebResearchContext context,
            WebSourceDocument document);

    /**
     * Persists a query-relevant evidence item from a source document.
     *
     * @param evidenceItemId stable evidence ID
     * @param sourceDocumentId owning source document ID
     * @param context execution context that produced the evidence
     * @param evidence evidence snippet
     * @param rankNo rank inside the extraction result
     */
    void insertEvidenceItem(
            UUID evidenceItemId,
            UUID sourceDocumentId,
            WebResearchContext context,
            WebEvidenceItem evidence,
            int rankNo);

    /**
     * Builds a compact prompt-safe summary of previously collected web research
     * artifacts for a Job.
     *
     * @param jobId Job ID
     * @param maxRows maximum rows included in the summary
     * @return prompt-safe summary; empty when no artifacts exist
     */
    String summarizeForJob(UUID jobId, int maxRows);
}
