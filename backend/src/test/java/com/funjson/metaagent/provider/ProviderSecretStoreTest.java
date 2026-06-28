package com.funjson.metaagent.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funjson.metaagent.provider.infrastructure.deepseek.ProviderSecretStore;
import org.junit.jupiter.api.Test;

/**
 * 验证密钥解析优先级和缺失行为。
 */
class ProviderSecretStoreTest {

    @Test
    void resolvesRequestThenMemoryThenEnvironmentWithoutExposingValue() {
        ProviderSecretStore store = new ProviderSecretStore("env-secret");

        assertThat(store.configured()).isTrue();
        assertThat(store.source()).isEqualTo("ENVIRONMENT");
        assertThat(store.require(null)).isEqualTo("env-secret");

        store.setMemorySecret("memory-secret");
        assertThat(store.source()).isEqualTo("MEMORY");
        assertThat(store.require(null)).isEqualTo("memory-secret");
        assertThat(store.require("request-secret")).isEqualTo("request-secret");
    }

    @Test
    void failsClearlyWhenNoSecretExists() {
        ProviderSecretStore store = new ProviderSecretStore(" ");

        assertThat(store.configured()).isFalse();
        assertThatThrownBy(() -> store.require(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }
}
