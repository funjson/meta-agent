package com.funjson.metaagent.websearch.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import com.funjson.metaagent.websearch.domain.FetchedWebDocument;
import com.funjson.metaagent.websearch.domain.WebEvidenceItem;
import com.funjson.metaagent.websearch.domain.WebSourceDocument;
import com.funjson.metaagent.websearch.domain.WebSourceType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Extracts readable text and lightweight evidence from fetched web documents.
 */
@Component
public class ReadableWebContentExtractor {

    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern META_DESCRIPTION_PATTERN = Pattern.compile(
            "(?is)<meta\\s+[^>]*(?:name|property)=[\"'](?:description|og:description)[\"'][^>]*content=[\"'](.*?)[\"'][^>]*>");
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile(
            "(?is)<(script|style|noscript|svg|canvas)[^>]*>.*?</\\1>");
    private static final Pattern TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
    private static final int MAX_EXCERPT_LENGTH = 420;

    /**
     * Converts raw fetched content into a source document.
     *
     * @param fetched fetched web document
     * @param maxChars maximum readable text length
     * @return source document
     */
    public WebSourceDocument extract(
            FetchedWebDocument fetched,
            int maxChars) {
        boolean html = fetched.contentType().toLowerCase().contains("html")
                || fetched.body().matches("(?is).*<html[\\s>].*");
        String title = html
                ? firstGroup(TITLE_PATTERN, fetched.body())
                : fetched.finalUrl();
        String description = html
                ? firstGroup(META_DESCRIPTION_PATTERN, fetched.body())
                : "";
        String text = html
                ? htmlToText(fetched.body())
                : fetched.body();
        text = truncate(normalize(text), maxChars);
        return new WebSourceDocument(
                fetched.finalUrl(),
                HtmlUtils.htmlUnescape(title),
                HtmlUtils.htmlUnescape(description),
                inferSourceType(fetched.finalUrl()),
                fetched.contentType(),
                fetched.fetchedAt(),
                sha256(text),
                text);
    }

    /**
     * Extracts query-relevant evidence snippets from a source document.
     *
     * @param document readable source document
     * @param query evidence query
     * @param maxEvidence maximum snippets
     * @return evidence items
     */
    public List<WebEvidenceItem> evidence(
            WebSourceDocument document,
            String query,
            int maxEvidence) {
        String normalizedQuery = query == null ? "" : query.toLowerCase();
        List<String> terms = java.util.Arrays.stream(
                        normalizedQuery.split("[\\s,，。；;:：]+"))
                .filter(term -> term.length() >= 2)
                .distinct()
                .toList();
        return java.util.Arrays.stream(document.text().split("\\n+|(?<=[。！？.!?])\\s+"))
                .map(String::trim)
                .filter(value -> value.length() >= 20)
                .map(value -> new ScoredExcerpt(value, score(value, terms)))
                .filter(excerpt -> terms.isEmpty() || excerpt.score() > 0)
                .sorted((left, right) -> Double.compare(
                        right.score(),
                        left.score()))
                .limit(Math.max(1, maxEvidence))
                .map(excerpt -> new WebEvidenceItem(
                        document.url(),
                        document.title(),
                        truncate(excerpt.value(), MAX_EXCERPT_LENGTH),
                        terms.isEmpty() ? 0.5 : excerpt.score(),
                        document.sourceType()))
                .toList();
    }

    /**
     * Removes noisy tags and decodes HTML entities.
     */
    private String htmlToText(String html) {
        String withoutNoise = SCRIPT_STYLE_PATTERN.matcher(html)
                .replaceAll(" ");
        String withLineBreaks = withoutNoise
                .replaceAll("(?is)</(p|div|li|h[1-6]|tr|section|article)>", "\n")
                .replaceAll("(?is)<br\\s*/?>", "\n");
        return HtmlUtils.htmlUnescape(
                TAG_PATTERN.matcher(withLineBreaks).replaceAll(" "));
    }

    /**
     * Returns the first regex group or an empty string.
     */
    private String firstGroup(Pattern pattern, String value) {
        var matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    /**
     * Normalizes whitespace while preserving paragraph boundaries.
     */
    private String normalize(String value) {
        return value.replace("\r", "\n")
                .replaceAll("[\\t ]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * Infers a coarse source category from URL host/path.
     */
    private WebSourceType inferSourceType(String url) {
        String lower = url == null ? "" : url.toLowerCase();
        if (lower.contains("arxiv.org")
                || lower.contains("doi.org")
                || lower.contains("scholar.google")
                || lower.endsWith(".pdf")) {
            return WebSourceType.PAPER;
        }
        if (lower.contains(".gov")
                || lower.contains(".edu")
                || lower.contains("/docs")
                || lower.contains("developer.")
                || lower.contains("docs.")) {
            return WebSourceType.OFFICIAL;
        }
        if (lower.contains("news")
                || lower.contains("reuters")
                || lower.contains("apnews")
                || lower.contains("bloomberg")) {
            return WebSourceType.NEWS;
        }
        if (lower.contains("stackoverflow")
                || lower.contains("reddit")
                || lower.contains("forum")) {
            return WebSourceType.FORUM;
        }
        if (lower.contains("blog")
                || lower.contains("medium.com")
                || lower.contains("substack")) {
            return WebSourceType.BLOG;
        }
        return WebSourceType.UNKNOWN;
    }

    /**
     * Scores an excerpt by query term coverage.
     */
    private double score(String value, List<String> terms) {
        if (terms.isEmpty()) {
            return 0.5;
        }
        String lower = value.toLowerCase();
        long hits = terms.stream().filter(lower::contains).count();
        return Math.min(1.0, (double) hits / terms.size());
    }

    /**
     * Truncates text safely for prompt/observation budgets.
     */
    private String truncate(String value, int maxChars) {
        int safeMax = Math.max(200, maxChars);
        return value.length() <= safeMax
                ? value
                : value.substring(0, safeMax - 3) + "...";
    }

    /**
     * Computes a content hash for dedupe and audit references.
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * Internal scored text holder.
     */
    private record ScoredExcerpt(String value, double score) {
    }
}
