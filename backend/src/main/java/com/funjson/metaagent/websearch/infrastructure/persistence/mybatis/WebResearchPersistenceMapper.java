package com.funjson.metaagent.websearch.infrastructure.persistence.mybatis;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for persisted web source documents and evidence items.
 */
@Mapper
public interface WebResearchPersistenceMapper {

    /** @return inserted row count */
    int insertSearchRun(
            @Param("id") UUID id,
            @Param("toolInvocationId") UUID toolInvocationId,
            @Param("jobId") UUID jobId,
            @Param("taskId") UUID taskId,
            @Param("taskRunId") UUID taskRunId,
            @Param("loopRunId") UUID loopRunId,
            @Param("loopNodeId") UUID loopNodeId,
            @Param("queryText") String queryText,
            @Param("recencyDays") Integer recencyDays,
            @Param("domainsJson") String domainsJson,
            @Param("locale") String locale,
            @Param("resultCount") int resultCount);

    /** @return inserted row count */
    int insertSearchCandidate(
            @Param("id") UUID id,
            @Param("searchRunId") UUID searchRunId,
            @Param("toolInvocationId") UUID toolInvocationId,
            @Param("jobId") UUID jobId,
            @Param("taskId") UUID taskId,
            @Param("taskRunId") UUID taskRunId,
            @Param("loopRunId") UUID loopRunId,
            @Param("loopNodeId") UUID loopNodeId,
            @Param("rankNo") int rankNo,
            @Param("title") String title,
            @Param("url") String url,
            @Param("snippet") String snippet,
            @Param("provider") String provider,
            @Param("sourceType") String sourceType,
            @Param("publishedAt") Timestamp publishedAt);

    /** @return inserted row count */
    int insertSourceDocument(
            @Param("id") UUID id,
            @Param("toolInvocationId") UUID toolInvocationId,
            @Param("jobId") UUID jobId,
            @Param("taskId") UUID taskId,
            @Param("taskRunId") UUID taskRunId,
            @Param("loopRunId") UUID loopRunId,
            @Param("loopNodeId") UUID loopNodeId,
            @Param("url") String url,
            @Param("title") String title,
            @Param("description") String description,
            @Param("sourceType") String sourceType,
            @Param("contentType") String contentType,
            @Param("contentHash") String contentHash,
            @Param("textExcerpt") String textExcerpt,
            @Param("fetchedAt") Timestamp fetchedAt);

    /** @return inserted row count */
    int insertEvidenceItem(
            @Param("id") UUID id,
            @Param("sourceDocumentId") UUID sourceDocumentId,
            @Param("toolInvocationId") UUID toolInvocationId,
            @Param("jobId") UUID jobId,
            @Param("taskId") UUID taskId,
            @Param("taskRunId") UUID taskRunId,
            @Param("loopRunId") UUID loopRunId,
            @Param("loopNodeId") UUID loopNodeId,
            @Param("rankNo") int rankNo,
            @Param("sourceUrl") String sourceUrl,
            @Param("title") String title,
            @Param("excerpt") String excerpt,
            @Param("relevanceScore") double relevanceScore,
            @Param("sourceType") String sourceType);

    /** @return rows used to build a prompt-safe research summary */
    List<Map<String, Object>> findResearchSummaryRows(
            @Param("jobId") UUID jobId,
            @Param("limit") int limit);
}
