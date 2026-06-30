package com.funjson.metaagent.websearch.application.port.out;

import java.util.List;

import com.funjson.metaagent.websearch.domain.WebSearchResult;
import com.funjson.metaagent.websearch.domain.WebSearchQuery;

/**
 * 外部 Web Search Provider 端口。
 */
public interface WebSearchClient {

    /**
     * 执行网络搜索。
     *
     * @param query 查询语句
     * @param limit 最大结果数
     * @return 搜索结果
     */
    List<WebSearchResult> search(WebSearchQuery query);

    /**
     * Compatibility helper for tests and direct callers.
     *
     * @param query query text
     * @param limit maximum result count
     * @return search results
     */
    default List<WebSearchResult> search(String query, int limit) {
        return search(new WebSearchQuery(
                query,
                limit,
                null,
                List.of(),
                ""));
    }
}
