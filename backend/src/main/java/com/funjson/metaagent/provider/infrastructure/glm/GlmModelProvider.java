package com.funjson.metaagent.provider.infrastructure.glm;

import com.funjson.metaagent.provider.domain.ModelProvider;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;
import org.springframework.stereotype.Component;

/**
 * GLM 模型 Provider Adapter。
 */
@Component
public class GlmModelProvider implements ModelProvider {

    private final GlmClient client;

    /**
     * 创建 GLM Provider。
     *
     * @param client GLM Client
     */
    public GlmModelProvider(GlmClient client) {
        this.client = client;
    }

    @Override
    public String providerId() {
        return "glm";
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        return client.generate(request, null);
    }

    @Override
    public boolean supportsNativeToolCalling() {
        return true;
    }

    @Override
    public boolean supportsNativeToolCalling(String modelId) {
        return client.supportsNativeToolCalling(modelId);
    }

    @Override
    public boolean supportsThinkingMode() {
        return true;
    }

    @Override
    public boolean supportsThinkingMode(String modelId) {
        return client.supportsThinkingMode(modelId);
    }
}
