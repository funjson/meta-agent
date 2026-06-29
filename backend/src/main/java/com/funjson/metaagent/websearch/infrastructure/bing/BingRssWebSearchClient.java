package com.funjson.metaagent.websearch.infrastructure.bing;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.websearch.application.port.out.WebSearchClient;
import com.funjson.metaagent.websearch.domain.WebSearchResult;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * 基于 Bing RSS 的无 Key 搜索适配器。
 */
@Component
public class BingRssWebSearchClient implements WebSearchClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_SNIPPET_LENGTH = 500;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public List<WebSearchResult> search(String query, int limit) {
        try {
            String encoded = URLEncoder.encode(
                    query,
                    StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.bing.com/search?q="
                            + encoded
                            + "&format=rss"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent",
                            "MetaAgentWebSearch/0.1 (+local-dev)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8));
            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new RuntimeStateException(
                        "WEB_SEARCH_PROVIDER_FAILED",
                        "Bing RSS returned HTTP " + response.statusCode());
            }
            return parse(response.body(), limit);
        } catch (RuntimeStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeStateException(
                    "WEB_SEARCH_PROVIDER_FAILED",
                    "Web search provider failed: "
                            + exception.getMessage());
        }
    }

    /**
     * 解析 Bing RSS XML。
     *
     * @param xml RSS XML
     * @param limit 最大结果数
     * @return 搜索结果
     */
    public List<WebSearchResult> parse(String xml, int limit) {
        try {
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            factory.setFeature(
                    XMLConstants.FEATURE_SECURE_PROCESSING,
                    true);
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true);
            factory.setExpandEntityReferences(false);
            var document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
            var items = document.getElementsByTagName("item");
            List<WebSearchResult> results = new ArrayList<>();
            Set<String> seenUrls = new LinkedHashSet<>();
            for (int index = 0; index < items.getLength()
                    && results.size() < limit; index++) {
                Element item = (Element) items.item(index);
                String url = text(item, "link");
                if (url.isBlank() || !seenUrls.add(url)) {
                    continue;
                }
                String title = text(item, "title");
                if (title.isBlank()) {
                    continue;
                }
                results.add(new WebSearchResult(
                        title,
                        url,
                        abbreviate(text(item, "description")),
                        parseDate(text(item, "pubDate"))));
            }
            return List.copyOf(results);
        } catch (Exception exception) {
            throw new RuntimeStateException(
                    "WEB_SEARCH_RESULT_PARSE_FAILED",
                    "Unable to parse search result feed");
        }
    }

    /**
     * 读取 item 子元素文本。
     */
    private String text(Element item, String tagName) {
        var values = item.getElementsByTagName(tagName);
        if (values.getLength() == 0 || values.item(0) == null) {
            return "";
        }
        return values.item(0).getTextContent().trim();
    }

    /**
     * 解析 RSS pubDate。
     */
    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(
                    value,
                    DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 截断摘要，避免工具结果污染上下文。
     */
    private String abbreviate(String value) {
        String normalized = value == null
                ? ""
                : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_SNIPPET_LENGTH
                ? normalized
                : normalized.substring(0, MAX_SNIPPET_LENGTH - 3) + "...";
    }
}
