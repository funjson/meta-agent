package com.funjson.metaagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.application.CapabilityApplicationService;
import com.funjson.metaagent.clarification.application.ClarificationService;
import com.funjson.metaagent.file.application.FileAttachmentService;
import com.funjson.metaagent.tool.application.ToolExecutionService;
import com.funjson.metaagent.tool.application.ToolInvocationCommand;
import com.funjson.metaagent.tool.application.port.out.ToolStore;
import com.funjson.metaagent.websearch.application.WebSearchService;
import com.funjson.metaagent.websearch.application.port.out.WebResearchStore;
import com.funjson.metaagent.websearch.domain.WebEvidenceExtraction;
import com.funjson.metaagent.websearch.domain.WebEvidenceItem;
import com.funjson.metaagent.websearch.domain.WebResearchContext;
import com.funjson.metaagent.websearch.domain.WebSearchQuery;
import com.funjson.metaagent.websearch.domain.WebSearchResult;
import com.funjson.metaagent.websearch.domain.WebSourceDocument;
import com.funjson.metaagent.websearch.domain.WebSourceType;
import com.funjson.metaagent.weather.application.WeatherService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Verifies that web tools persist formal evidence pool artifacts.
 */
class ToolExecutionServiceWebResearchTest {

    @Test
    void webSearchPersistsSearchRunAndCandidates() {
        ToolStore toolStore = mock(ToolStore.class);
        WebSearchService webSearchService = mock(WebSearchService.class);
        WebResearchStore webResearchStore = mock(WebResearchStore.class);
        ToolExecutionService service = new ToolExecutionService(
                toolStore,
                mock(CapabilityApplicationService.class),
                mock(ClarificationService.class),
                mock(FileAttachmentService.class),
                webSearchService,
                webResearchStore,
                mock(WeatherService.class),
                new ObjectMapper());
        UUID jobId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID taskRunId = UUID.randomUUID();
        UUID loopRunId = UUID.randomUUID();
        UUID loopNodeId = UUID.randomUUID();
        WebSearchResult official = new WebSearchResult(
                "Official weather",
                "https://example.com/weather",
                "Beijing weather forecast",
                Instant.parse("2026-06-29T00:00:00Z"),
                "test",
                1,
                WebSourceType.OFFICIAL);
        WebSearchResult article = new WebSearchResult(
                "Weather analysis",
                "https://example.com/analysis",
                "Analysis with forecast context",
                null,
                "test",
                2,
                WebSourceType.UNKNOWN);

        when(toolStore.findInvocationByIdempotencyKey("web-search-test"))
                .thenReturn(Optional.empty());
        when(webSearchService.search(any(WebSearchQuery.class)))
                .thenReturn(List.of(official, article));

        var view = service.invoke(new ToolInvocationCommand(
                "web.search",
                Map.of(
                        "query", "北京天气",
                        "limit", 2,
                        "locale", "zh-CN"),
                "web-search-test",
                jobId,
                taskId,
                taskRunId,
                loopRunId,
                loopNodeId));

        ArgumentCaptor<UUID> searchRunIdCaptor =
                ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<WebResearchContext> contextCaptor =
                ArgumentCaptor.forClass(WebResearchContext.class);
        verify(webResearchStore).insertSearchRun(
                searchRunIdCaptor.capture(),
                contextCaptor.capture(),
                any(WebSearchQuery.class),
                eq(2));
        verify(webResearchStore).insertSearchCandidate(
                any(UUID.class),
                eq(searchRunIdCaptor.getValue()),
                eq(contextCaptor.getValue()),
                eq(official));
        verify(webResearchStore).insertSearchCandidate(
                any(UUID.class),
                eq(searchRunIdCaptor.getValue()),
                eq(contextCaptor.getValue()),
                eq(article));
        assertThat(contextCaptor.getValue().jobId()).isEqualTo(jobId);
        assertThat(view.result()).containsKeys(
                "searchRunId",
                "toolId",
                "toolType");
    }

    @Test
    void webExtractPersistsSourceAndEvidenceItems() {
        ToolStore toolStore = mock(ToolStore.class);
        WebSearchService webSearchService = mock(WebSearchService.class);
        WebResearchStore webResearchStore = mock(WebResearchStore.class);
        ToolExecutionService service = new ToolExecutionService(
                toolStore,
                mock(CapabilityApplicationService.class),
                mock(ClarificationService.class),
                mock(FileAttachmentService.class),
                webSearchService,
                webResearchStore,
                mock(WeatherService.class),
                new ObjectMapper());
        WebSourceDocument document = new WebSourceDocument(
                "https://example.com",
                "Example",
                "Example description",
                WebSourceType.OFFICIAL,
                "text/html",
                Instant.parse("2026-06-29T00:00:00Z"),
                "f".repeat(64),
                "This source contains useful evidence for research.");
        List<WebEvidenceItem> evidence = List.of(
                new WebEvidenceItem(
                        "https://example.com",
                        "Example",
                        "Useful evidence for research.",
                        0.9,
                        WebSourceType.OFFICIAL),
                new WebEvidenceItem(
                        "https://example.com",
                        "Example",
                        "Second supporting excerpt.",
                        0.7,
                        WebSourceType.OFFICIAL));
        UUID jobId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID taskRunId = UUID.randomUUID();
        UUID loopRunId = UUID.randomUUID();
        UUID loopNodeId = UUID.randomUUID();

        when(toolStore.findInvocationByIdempotencyKey("web-extract-test"))
                .thenReturn(Optional.empty());
        when(webSearchService.extract("https://example.com", "research", 2))
                .thenReturn(new WebEvidenceExtraction(document, evidence));

        var view = service.invoke(new ToolInvocationCommand(
                "web.extract",
                Map.of(
                        "url", "https://example.com",
                        "query", "research",
                        "maxEvidence", 2),
                "web-extract-test",
                jobId,
                taskId,
                taskRunId,
                loopRunId,
                loopNodeId));

        ArgumentCaptor<UUID> sourceIdCaptor =
                ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<WebResearchContext> contextCaptor =
                ArgumentCaptor.forClass(WebResearchContext.class);
        verify(webResearchStore).insertSourceDocument(
                sourceIdCaptor.capture(),
                contextCaptor.capture(),
                eq(document));
        verify(webResearchStore).insertEvidenceItem(
                any(UUID.class),
                eq(sourceIdCaptor.getValue()),
                eq(contextCaptor.getValue()),
                eq(evidence.get(0)),
                eq(1));
        verify(webResearchStore).insertEvidenceItem(
                any(UUID.class),
                eq(sourceIdCaptor.getValue()),
                eq(contextCaptor.getValue()),
                eq(evidence.get(1)),
                eq(2));
        assertThat(contextCaptor.getValue().loopNodeId()).isEqualTo(loopNodeId);
        assertThat(view.result()).containsKey("sourceDocumentId");
    }
}
