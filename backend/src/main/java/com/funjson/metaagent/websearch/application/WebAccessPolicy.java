package com.funjson.metaagent.websearch.application;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

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
