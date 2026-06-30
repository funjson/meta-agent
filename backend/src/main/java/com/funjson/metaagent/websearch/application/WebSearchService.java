package com.funjson.metaagent.websearch.application;

import java.net.URI;
import java.util.List;

import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.websearch.application.port.out.WebDocumentFetcher;
import com.funjson.metaagent.websearch.application.port.out.WebSearchClient;
import com.funjson.metaagent.websearch.domain.WebEvidenceExtraction;
import com.funjson.metaagent.websearch.domain.WebEvidenceItem;
import com.funjson.metaagent.websearch.domain.WebSearchResult;
import com.funjson.metaagent.websearch.domain.WebSearchQuery;
import com.funjson.metaagent.websearch.domain.WebSourceDocument;
import org.springframework.stereotype.Service;

/**
 * Web Search 应用服务，负责查询边界和结果裁剪。
 */
@Service
public class WebSearchService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 8;
    private static final int MAX_QUERY_LENGTH = 300;
    private static final int DEFAULT_FETCH_CHARS = 8_000;
    private static final int MAX_FETCH_CHARS = 24_000;
    private static final int DEFAULT_FETCH_BYTES = 500_000;

    private final WebSearchClient client;
    private final WebDocumentFetcher documentFetcher;
    private final ReadableWebContentExtractor contentExtractor;

    /**
     * 创建 Web Search Service。
     *
     * @param client 外部搜索客户端
     * @param documentFetcher 网页读取适配器
     * @param contentExtractor 正文与证据抽取器
     */
    public WebSearchService(
            WebSearchClient client,
            WebDocumentFetcher documentFetcher,
            ReadableWebContentExtractor contentExtractor) {
        this.client = client;
        this.documentFetcher = documentFetcher;
        this.contentExtractor = contentExtractor;
    }

    /**
     * 执行网络搜索。
     *
     * @param query 查询语句
     * @param limit 最大结果数
     * @return 搜索结果
     */
    public List<WebSearchResult> search(String query, int limit) {
        return search(new WebSearchQuery(
                normalizeQuery(query),
                boundedLimit(limit),
                null,
                List.of(),
                ""));
    }

    /**
     * 执行结构化网络搜索。
     *
     * @param query 搜索请求
     * @return 搜索结果
     */
    public List<WebSearchResult> search(WebSearchQuery query) {
        return client.search(new WebSearchQuery(
                normalizeQuery(query.query()),
                boundedLimit(query.limit()),
                query.recencyDays(),
                query.domains(),
                query.locale()));
    }

    /**
     * 读取并清洗公开网页。
     *
     * @param url URL
     * @param maxChars 最大正文字符数
     * @return 可引用来源文档
     */
    public WebSourceDocument fetch(String url, int maxChars) {
        URI uri = parseUri(url);
        int boundedChars = Math.max(
                1_000,
                Math.min(maxChars <= 0 ? DEFAULT_FETCH_CHARS : maxChars,
                        MAX_FETCH_CHARS));
        var fetched = documentFetcher.fetch(uri, DEFAULT_FETCH_BYTES);
        return contentExtractor.extract(fetched, boundedChars);
    }

    /**
     * 读取网页并抽取与 query 相关的证据片段。
     *
     * @param url URL
     * @param query 证据查询
     * @param maxEvidence 最大证据数
     * @return 证据片段
     */
    public List<WebEvidenceItem> extractEvidence(
            String url,
            String query,
            int maxEvidence) {
        return extract(url, query, maxEvidence).evidence();
    }

    /**
     * 读取网页并返回来源文档与证据片段。
     *
     * @param url URL
     * @param query 证据查询
     * @param maxEvidence 最大证据数
     * @return 来源文档和证据列表
     */
    public WebEvidenceExtraction extract(
            String url,
            String query,
            int maxEvidence) {
        WebSourceDocument document = fetch(url, DEFAULT_FETCH_CHARS);
        List<WebEvidenceItem> evidence = contentExtractor.evidence(
                document,
                query,
                Math.max(1, Math.min(maxEvidence <= 0 ? 5 : maxEvidence, 12)));
        return new WebEvidenceExtraction(document, evidence);
    }

    /**
     * 规范化并裁剪 query。
     */
    private String normalizeQuery(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank()) {
            throw new RuntimeStateException(
                    "WEB_SEARCH_QUERY_EMPTY",
                    "Web search query is required");
        }
        if (normalized.length() > MAX_QUERY_LENGTH) {
            normalized = normalized.substring(0, MAX_QUERY_LENGTH);
        }
        return normalized;
    }

    /**
     * 约束搜索结果数量。
     */
    private int boundedLimit(int limit) {
        return Math.max(
                1,
                Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }

    /**
     * Parses URL text into a URI with consistent error codes.
     */
    private URI parseUri(String url) {
        try {
            return URI.create(url == null ? "" : url.trim());
        } catch (IllegalArgumentException exception) {
            throw new RuntimeStateException(
                    "WEB_URL_INVALID",
                    "Web URL is invalid");
        }
    }
}
