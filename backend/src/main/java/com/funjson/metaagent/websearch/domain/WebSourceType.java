package com.funjson.metaagent.websearch.domain;

/**
 * Coarse source category used for source selection and citation policy.
 */
public enum WebSourceType {
    /** Official vendor, government, standards or product documentation. */
    OFFICIAL,
    /** Academic paper, preprint or DOI-backed source. */
    PAPER,
    /** News or media article. */
    NEWS,
    /** Blog, tutorial or personal publication. */
    BLOG,
    /** Forum, Q&A or social content. */
    FORUM,
    /** Source type could not be inferred safely. */
    UNKNOWN
}
