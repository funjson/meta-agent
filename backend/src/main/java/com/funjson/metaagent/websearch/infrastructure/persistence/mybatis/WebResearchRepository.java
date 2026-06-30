package com.funjson.metaagent.websearch.infrastructure.persistence.mybatis;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.websearch.application.port.out.WebResearchStore;
import com.funjson.metaagent.websearch.domain.WebEvidenceItem;
import com.funjson.metaagent.websearch.domain.WebResearchContext;
import com.funjson.metaagent.websearch.domain.WebSearchQuery;
import com.funjson.metaagent.websearch.domain.WebSearchResult;
import com.funjson.metaagent.websearch.domain.WebSourceDocument;
import org.springframework.stereotype.Repository;

/**
 * MyBatis adapter for the web research evidence pool.
 */
@Repository
public class WebResearchRepository implements WebResearchStore {

    private static final int STORED_SOURCE_TEXT_LIMIT = 24_000;

    private final WebResearchPersistenceMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * Creates the repository.
     *
     * @param mapper MyBatis mapper
     * @param objectMapper JSON mapper
     */
    public WebResearchRepository(
            WebResearchPersistenceMapper mapper,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    public void insertSearchRun(
            UUID searchRunId,
            WebResearchContext context,
            WebSearchQuery query,
            int resultCount) {
        mapper.insertSearchRun(
                searchRunId,
                context.toolInvocationId(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                context.loopRunId(),
                context.loopNodeId(),
                query.query(),
                query.recencyDays(),
                json(query.domains()),
                query.locale(),
                Math.max(0, resultCount));
    }

    /** {@inheritDoc} */
    @Override
    public void insertSearchCandidate(
            UUID candidateId,
            UUID searchRunId,
            WebResearchContext context,
            WebSearchResult result) {
        mapper.insertSearchCandidate(
                candidateId,
                searchRunId,
                context.toolInvocationId(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                context.loopRunId(),
                context.loopNodeId(),
                Math.max(1, result.rank()),
                result.title(),
                result.url(),
                result.snippet(),
                result.provider(),
                result.sourceType().name(),
                result.publishedAt() == null
                        ? null
                        : Timestamp.from(result.publishedAt()));
    }

    /** {@inheritDoc} */
    @Override
    public void insertSourceDocument(
            UUID sourceDocumentId,
            WebResearchContext context,
            WebSourceDocument document) {
        mapper.insertSourceDocument(
                sourceDocumentId,
                context.toolInvocationId(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                context.loopRunId(),
                context.loopNodeId(),
                document.url(),
                document.title(),
                document.description(),
                document.sourceType().name(),
                document.contentType(),
                document.contentHash(),
                truncate(document.text(), STORED_SOURCE_TEXT_LIMIT),
                Timestamp.from(document.fetchedAt()));
    }

    /** {@inheritDoc} */
    @Override
    public void insertEvidenceItem(
            UUID evidenceItemId,
            UUID sourceDocumentId,
            WebResearchContext context,
            WebEvidenceItem evidence,
            int rankNo) {
        mapper.insertEvidenceItem(
                evidenceItemId,
                sourceDocumentId,
                context.toolInvocationId(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                context.loopRunId(),
                context.loopNodeId(),
                Math.max(1, rankNo),
                evidence.sourceUrl(),
                evidence.title(),
                evidence.excerpt(),
                evidence.relevanceScore(),
                evidence.sourceType().name());
    }

    /** {@inheritDoc} */
    @Override
    public String summarizeForJob(UUID jobId, int maxRows) {
        if (jobId == null) {
            return "";
        }
        List<Map<String, Object>> rows = mapper.findResearchSummaryRows(
                jobId,
                Math.max(1, Math.min(maxRows, 80)));
        if (rows.isEmpty()) {
            return "";
        }
        return rows.stream()
                .map(this::summaryLine)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    /**
     * Stores a bounded source excerpt to keep the evidence pool inspectable
     * without turning it into an unbounded web cache.
     */
    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars - 3) + "...";
    }

    /**
     * Serializes a small JSON payload for JSON columns.
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null
                    ? List.of()
                    : value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    /**
     * Converts a search/source/evidence row into a prompt-safe single line.
     */
    private String summaryLine(Map<String, Object> row) {
        String type = text(row.get("artifactType"));
        String title = text(row.get("title"));
        String summary = text(row.get("summary"));
        String url = text(row.get("url"));
        String suffix = url.isBlank() ? "" : " · " + url;
        return "- %s · %s: %s%s".formatted(
                type,
                title,
                summary,
                suffix);
    }

    /**
     * Reads nullable text from a MyBatis result row.
     */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
