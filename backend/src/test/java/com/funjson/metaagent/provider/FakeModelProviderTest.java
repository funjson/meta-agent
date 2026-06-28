package com.funjson.metaagent.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.funjson.metaagent.prompt.domain.RenderedPrompt;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;
import com.funjson.metaagent.provider.infrastructure.fake.FakeModelProvider;
import org.junit.jupiter.api.Test;

/**
 * 验证 Fake Provider 的确定性输出。
 */
class FakeModelProviderTest {

    @Test
    void returnsDeterministicEvidenceFriendlyResponse() {
        FakeModelProvider provider = new FakeModelProvider(
                new NoOpModelCallRepository());
        ModelRequest request = new ModelRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "验证 Loop 闭环",
                new RenderedPrompt(
                        "test",
                        "v1",
                        "system",
                        "验证 Loop 闭环",
                        "hash"),
                128);

        ModelResponse first = provider.generate(request);
        ModelResponse second = provider.generate(request);

        assertThat(first)
                .isEqualTo(second)
                .extracting(ModelResponse::provider, ModelResponse::model, ModelResponse::finishReason)
                .containsExactly("fake", "fake-deterministic-v1", "STOP");
        assertThat(first.content()).contains("验证 Loop 闭环");
    }

}
