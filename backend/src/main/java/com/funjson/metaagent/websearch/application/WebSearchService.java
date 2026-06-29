package com.funjson.metaagent.websearch.application;

import java.util.List;

import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.websearch.application.port.out.WebSearchClient;
import com.funjson.metaagent.websearch.domain.WebSearchResult;
import org.springframework.stereotype.Service;

/**
 * Web Search 应用服务，负责查询边界和结果裁剪。
 */
@Service
public class WebSearchService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 8;
    private static final int MAX_QUERY_LENGTH = 300;

    private final WebSearchClient client;

    /**
     * 创建 Web Search Service。
     *
     * @param client 外部搜索客户端
     */
    public WebSearchService(WebSearchClient client) {
        this.client = client;
    }

    /**
     * 执行网络搜索。
     *
     * @param query 查询语句
     * @param limit 最大结果数
     * @return 搜索结果
     */
    public List<WebSearchResult> search(String query, int limit) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank()) {
            throw new RuntimeStateException(
                    "WEB_SEARCH_QUERY_EMPTY",
                    "Web search query is required");
        }
        if (normalized.length() > MAX_QUERY_LENGTH) {
            normalized = normalized.substring(0, MAX_QUERY_LENGTH);
        }
        int boundedLimit = Math.max(
                1,
                Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT));
        return client.search(normalized, boundedLimit);
    }
}
