package com.funjson.metaagent.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import com.funjson.metaagent.websearch.application.ReadableWebContentExtractor;
import com.funjson.metaagent.websearch.application.WebSearchService;
import com.funjson.metaagent.websearch.application.port.out.WebDocumentFetcher;
import com.funjson.metaagent.websearch.application.port.out.WebSearchClient;
import com.funjson.metaagent.websearch.domain.FetchedWebDocument;
import com.funjson.metaagent.websearch.domain.WebSearchQuery;
import com.funjson.metaagent.websearch.domain.WebSearchResult;
import com.funjson.metaagent.websearch.domain.WebSourceType;
import org.junit.jupiter.api.Test;

/**
 * Verifies the application-level web search contracts.
 */
class WebSearchServiceTest {

    @Test
    void passesStructuredSearchHintsToProvider() {
        CapturingSearchClient searchClient = new CapturingSearchClient();
        WebSearchService service = new WebSearchService(
                searchClient,
                fakeFetcher(),
                new ReadableWebContentExtractor());

        service.search(new WebSearchQuery(
                " OpenAI deep research ",
                20,
                7,
                List.of("openai.com"),
                "zh-CN"));

        assertThat(searchClient.lastQuery.limit()).isEqualTo(8);
        assertThat(searchClient.lastQuery.recencyDays()).isEqualTo(7);
        assertThat(searchClient.lastQuery.domains())
                .containsExactly("openai.com");
    }

    @Test
    void fetchReturnsReadableSourceDocument() {
        WebSearchService service = new WebSearchService(
                query -> List.of(),
                fakeFetcher(),
                new ReadableWebContentExtractor());

        var document = service.fetch("https://example.com/page", 2_000);

        assertThat(document.title()).isEqualTo("Example Page");
        assertThat(document.text()).contains("search quality evidence");
        assertThat(document.contentHash()).hasSize(64);
    }

    @Test
    void extractReturnsSourceDocumentAndEvidenceTogether() {
        WebSearchService service = new WebSearchService(
                query -> List.of(),
                fakeFetcher(),
                new ReadableWebContentExtractor());

        var extraction = service.extract(
                "https://example.com/page",
                "search quality",
                3);

        assertThat(extraction.document().url())
                .isEqualTo("https://example.com/page");
        assertThat(extraction.evidence())
                .hasSize(1)
                .first()
                .satisfies(item -> assertThat(item.excerpt())
                        .contains("search quality evidence"));
    }

    /**
     * @return fake fetcher for deterministic tests
     */
    private WebDocumentFetcher fakeFetcher() {
        return (URI uri, int maxBytes) -> new FetchedWebDocument(
                uri.toString(),
                uri.toString(),
                "text/html",
                """
                <html><head><title>Example Page</title></head>
                <body><p>This page contains search quality evidence.</p></body></html>
                """,
                Instant.parse("2026-06-29T00:00:00Z"));
    }

    /**
     * Captures structured query values.
     */
    private static final class CapturingSearchClient implements WebSearchClient {

        private WebSearchQuery lastQuery;

        @Override
        public List<WebSearchResult> search(WebSearchQuery query) {
            this.lastQuery = query;
            return List.of(new WebSearchResult(
                    "OpenAI",
                    "https://openai.com",
                    "snippet",
                    null,
                    "test",
                    1,
                    WebSourceType.OFFICIAL));
        }
    }
}
