package com.funjson.metaagent.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.funjson.metaagent.provider.application.ModelProviderRegistry;
import com.funjson.metaagent.provider.infrastructure.fake.FakeModelProvider;
import org.junit.jupiter.api.Test;

/**
 * 验证 Provider Registry 的稳定 ID 解析。
 */
class ModelProviderRegistryTest {

    @Test
    void resolvesProviderByStableId() {
        FakeModelProvider fake = new FakeModelProvider(
                new NoOpModelCallRepository());
        ModelProviderRegistry registry = new ModelProviderRegistry(List.of(fake));

        assertThat(registry.require("fake")).isSameAs(fake);
        assertThatThrownBy(() -> registry.require("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }
}
