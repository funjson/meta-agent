package com.funjson.metaagent.websearch.infrastructure.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.websearch.application.WebAccessPolicy;
import com.funjson.metaagent.websearch.application.port.out.WebDocumentFetcher;
import com.funjson.metaagent.websearch.domain.FetchedWebDocument;
import org.springframework.stereotype.Component;

/**
 * Java HTTP based document fetcher for public web pages.
 */
@Component
public class JavaHttpWebDocumentFetcher implements WebDocumentFetcher {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int HARD_MAX_BYTES = 1_000_000;
    private static final int MAX_REDIRECTS = 3;

    private final WebAccessPolicy accessPolicy;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * Creates the fetcher.
     *
     * @param accessPolicy URL safety policy
     */
    public JavaHttpWebDocumentFetcher(WebAccessPolicy accessPolicy) {
        this.accessPolicy = accessPolicy;
    }

    @Override
    public FetchedWebDocument fetch(URI uri, int maxBytes) {
        return fetch(uri, maxBytes, 0);
    }

    /**
     * Fetches a URL, validating every redirect target before following it.
     */
    private FetchedWebDocument fetch(URI uri, int maxBytes, int redirectCount) {
        accessPolicy.requireFetchableDocumentUri(uri);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent",
                            "MetaAgentWebResearch/0.1 (+https://github.com/funjson/meta-agent)")
                    .header("Accept",
                            "text/html,application/xhtml+xml,text/plain,application/pdf;q=0.5,*/*;q=0.1")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (isRedirect(response.statusCode())) {
                return followRedirect(uri, response, maxBytes, redirectCount);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeStateException(
                        "WEB_FETCH_PROVIDER_FAILED",
                        "Web fetch returned HTTP " + response.statusCode());
            }
            String contentType = response.headers()
                    .firstValue("content-type")
                    .orElse("");
            byte[] body = truncate(
                    response.body(),
                    Math.min(
                            Math.max(maxBytes, 1_024),
                            HARD_MAX_BYTES));
            return new FetchedWebDocument(
                    uri.toString(),
                    response.uri().toString(),
                    contentType,
                    new String(body, charset(contentType)),
                    Instant.now());
        } catch (RuntimeStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeStateException(
                    "WEB_FETCH_FAILED",
                    "Unable to fetch web document: " + exception.getMessage());
        }
    }

    /**
     * Follows a redirect only after validating the resolved target URL.
     */
    private FetchedWebDocument followRedirect(
            URI currentUri,
            HttpResponse<byte[]> response,
            int maxBytes,
            int redirectCount) {
        if (redirectCount >= MAX_REDIRECTS) {
            throw new RuntimeStateException(
                    "WEB_FETCH_REDIRECT_LIMIT",
                    "Web fetch redirect limit exceeded");
        }
        String location = response.headers()
                .firstValue("location")
                .orElse("");
        if (location.isBlank()) {
            throw new RuntimeStateException(
                    "WEB_FETCH_REDIRECT_INVALID",
                    "Web fetch redirect is missing Location header");
        }
        URI target = currentUri.resolve(location);
        // Re-enter the public-web policy for every hop so an apparently public
        // URL cannot bounce the fetcher into localhost, private networks, or a
        // search result page that should have gone through web.search.
        accessPolicy.requireFetchableDocumentUri(target);
        return fetch(target, maxBytes, redirectCount + 1);
    }

    /**
     * @return true when the HTTP status is a redirect.
     */
    private boolean isRedirect(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }

    /**
     * Reads charset from content type; defaults to UTF-8.
     */
    private Charset charset(String contentType) {
        String lower = contentType == null ? "" : contentType.toLowerCase();
        int index = lower.indexOf("charset=");
        if (index < 0) {
            return StandardCharsets.UTF_8;
        }
        String value = lower.substring(index + "charset=".length())
                .replaceAll("[; ].*$", "")
                .replace("\"", "")
                .trim();
        try {
            return Charset.forName(value);
        } catch (RuntimeException exception) {
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * Keeps only the configured number of response bytes.
     */
    private byte[] truncate(byte[] value, int maxBytes) {
        if (value.length <= maxBytes) {
            return value;
        }
        return java.util.Arrays.copyOf(value, maxBytes);
    }
}
