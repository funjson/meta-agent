package com.funjson.metaagent.websearch.application.port.out;

import java.util.List;

import com.funjson.metaagent.websearch.domain.WebSearchResult;

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
    List<WebSearchResult> search(String query, int limit);
}
