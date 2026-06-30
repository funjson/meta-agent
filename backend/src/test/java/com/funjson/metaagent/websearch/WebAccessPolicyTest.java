package com.funjson.metaagent.websearch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.websearch.application.WebAccessPolicy;
import org.junit.jupiter.api.Test;

/**
 * Verifies SSRF safety boundaries for web fetch tools.
 */
class WebAccessPolicyTest {

    private final WebAccessPolicy policy = new WebAccessPolicy();

    @Test
    void rejectsLoopbackUrl() {
        assertThatThrownBy(() -> policy.requirePublicHttpUri(
                URI.create("http://127.0.0.1:8080/actuator/health")))
                .isInstanceOf(RuntimeStateException.class)
                .hasMessageContaining("private or local");
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertThatThrownBy(() -> policy.requirePublicHttpUri(
                URI.create("ftp://example.com/a.txt")))
                .isInstanceOf(RuntimeStateException.class)
                .hasMessageContaining("http and https");
    }
}
