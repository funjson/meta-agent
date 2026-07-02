package com.funjson.metaagent.websearch.application;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.stereotype.Component;

/**
 * Guards web tools against SSRF-style access to local or private resources.
 */
@Component
public class WebAccessPolicy {

    /**
     * Validates that a URI targets a public HTTP(S) host.
     *
     * @param uri candidate URI
     */
    public void requirePublicHttpUri(URI uri) {
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            throw new RuntimeStateException(
                    "WEB_URL_INVALID",
                    "Web URL must include scheme and host");
        }
        String scheme = uri.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new RuntimeStateException(
                    "WEB_URL_SCHEME_DENIED",
                    "Only http and https URLs are allowed");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new RuntimeStateException(
                    "WEB_URL_USERINFO_DENIED",
                    "URLs with userinfo are not allowed");
        }
        validateHost(uri.getHost());
    }

    /**
     * Validates that a URI can be fetched as a concrete source document.
     *
     * <p>Search engine result pages are discovery surfaces, not evidence
     * documents. Fetching them is brittle, frequently blocked, and causes the
     * model to confuse “search page” with “source page”; callers should use
     * web.search first, then fetch URLs returned by search candidates.</p>
     *
     * @param uri candidate URI
     */
    public void requireFetchableDocumentUri(URI uri) {
        requirePublicHttpUri(uri);
        if (isSearchEngineResultPage(uri)) {
            throw new RuntimeStateException(
                    "WEB_FETCH_SEARCH_RESULT_DENIED",
                    "web.fetch cannot read search result pages; "
                            + "use web.search and then fetch concrete "
                            + "candidate source URLs");
        }
    }

    /**
     * Detects common public search result URLs that should not be fetched.
     *
     * @param uri candidate URI
     * @return true when the URL is a SERP rather than an evidence document
     */
    private boolean isSearchEngineResultPage(URI uri) {
        String host = normalizeHost(uri.getHost());
        String path = uri.getPath() == null
                ? ""
                : uri.getPath().toLowerCase(Locale.ROOT);
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
        if (host.endsWith("google.com") && path.startsWith("/search")) {
            return true;
        }
        if (host.endsWith("bing.com") && path.startsWith("/search")) {
            return true;
        }
        if (host.endsWith("duckduckgo.com")
                && (path.isBlank() || "/".equals(path) || "/html".equals(path))
                && query.contains("q=")) {
            return true;
        }
        if (host.endsWith("baidu.com") && path.startsWith("/s")) {
            return true;
        }
        if (host.endsWith("yahoo.com") && path.startsWith("/search")) {
            return true;
        }
        return host.endsWith("yandex.com") && path.startsWith("/search");
    }

    /**
     * @return lowercase host without the common www prefix.
     */
    private String normalizeHost(String host) {
        String normalized = host == null
                ? ""
                : host.toLowerCase(Locale.ROOT);
        return normalized.startsWith("www.")
                ? normalized.substring(4)
                : normalized;
    }

    /**
     * Resolves the host and rejects private, loopback and link-local targets.
     */
    private void validateHost(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isDenied(address)) {
                    throw new RuntimeStateException(
                            "WEB_URL_PRIVATE_HOST_DENIED",
                            "Web tools cannot access private or local hosts");
                }
            }
        } catch (UnknownHostException exception) {
            throw new RuntimeStateException(
                    "WEB_URL_HOST_UNRESOLVED",
                    "Unable to resolve web host: " + host);
        }
    }

    /**
     * Checks Java address flags plus IPv6 unique-local ranges.
     */
    private boolean isDenied(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isIpv6UniqueLocal(address);
    }

    /**
     * @return true when the address is fc00::/7.
     */
    private boolean isIpv6UniqueLocal(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte first = address.getAddress()[0];
        return (first & (byte) 0xFE) == (byte) 0xFC;
    }
}
