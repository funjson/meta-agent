package com.funjson.metaagent.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.funjson.metaagent.websearch.infrastructure.bing.BingRssWebSearchClient;
import org.junit.jupiter.api.Test;

/**
 * 验证 Bing RSS 搜索结果解析。
 */
class BingRssWebSearchClientTest {

    @Test
    void parsesRssItems() {
        BingRssWebSearchClient client = new BingRssWebSearchClient();

        var results = client.parse(
                """
                <?xml version="1.0" encoding="utf-8" ?>
                <rss version="2.0">
                  <channel>
                    <item>
                      <title>Example Result</title>
                      <link>https://example.com/a</link>
                      <description>Snippet text</description>
                      <pubDate>Sat, 27 Jun 2026 08:14:00 GMT</pubDate>
                    </item>
                  </channel>
                </rss>
                """,
                5);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().title()).isEqualTo("Example Result");
        assertThat(results.getFirst().url()).isEqualTo(
                "https://example.com/a");
        assertThat(results.getFirst().snippet()).isEqualTo("Snippet text");
        assertThat(results.getFirst().publishedAt()).isNotNull();
    }
}
