package com.funjson.metaagent.websearch.application.port.out;

import java.net.URI;

import com.funjson.metaagent.websearch.domain.FetchedWebDocument;

/**
 * Fetches external web documents after URL safety validation.
 */
public interface WebDocumentFetcher {

    /**
     * Fetches a document from the public web.
     *
     * @param uri public HTTP(S) URI
     * @param maxBytes maximum bytes retained from the response body
     * @return fetched document
     */
    FetchedWebDocument fetch(URI uri, int maxBytes);
}
