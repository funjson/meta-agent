package com.funjson.metaagent.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.funjson.metaagent.websearch.application.ReadableWebContentExtractor;
import com.funjson.metaagent.websearch.domain.FetchedWebDocument;
import com.funjson.metaagent.websearch.domain.WebSourceType;
import org.junit.jupiter.api.Test;

/**
 * Verifies readable content and evidence extraction.
 */
class ReadableWebContentExtractorTest {

    private final ReadableWebContentExtractor extractor =
            new ReadableWebContentExtractor();

    @Test
    void extractsTitleTextAndEvidence() {
        var document = extractor.extract(new FetchedWebDocument(
                "https://docs.example.com/agent",
                "https://docs.example.com/agent",
                "text/html; charset=utf-8",
                """
                <html>
                  <head><title>Agent Docs</title><script>ignore()</script></head>
                  <body>
                    <h1>Agent Platform</h1>
                    <p>Deep research uses search, fetch and evidence extraction.</p>
                    <p>Ordinary chat can answer without external sources.</p>
                  </body>
                </html>
                """,
                Instant.parse("2026-06-29T00:00:00Z")),
                2_000);

        assertThat(document.title()).isEqualTo("Agent Docs");
        assertThat(document.text()).contains("Deep research uses search");
        assertThat(document.text()).doesNotContain("ignore()");
        assertThat(document.sourceType()).isEqualTo(WebSourceType.OFFICIAL);

        var evidence = extractor.evidence(
                document,
                "deep research evidence",
                3);

        assertThat(evidence).hasSize(1);
        assertThat(evidence.getFirst().excerpt())
                .contains("Deep research uses search");
        assertThat(evidence.getFirst().relevanceScore()).isGreaterThan(0);
    }
}
