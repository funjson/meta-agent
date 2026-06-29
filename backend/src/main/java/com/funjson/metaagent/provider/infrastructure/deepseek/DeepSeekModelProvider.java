package com.funjson.metaagent.provider.infrastructure.deepseek;

import com.funjson.metaagent.provider.domain.ModelProvider;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;
import org.springframework.stereotype.Component;

/**
 * 将框架无关 ModelProvider 端口适配到 DeepSeek Client。
 */
@Component
public class DeepSeekModelProvider implements ModelProvider {

    private final DeepSeekClient client;

    /**
     * 创建 DeepSeek Provider。
     *
     * @param client DeepSeek Client
     */
    public DeepSeekModelProvider(DeepSeekClient client) {
        this.client = client;
    }

    @Override
    public String providerId() {
        return "deepseek";
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        return client.generate(request, null);
    }

    @Override
    public boolean supportsNativeToolCalling() {
        return client.supportsNativeToolCalling();
    }

    @Override
    public boolean supportsNativeToolCalling(String modelId) {
        return client.supportsNativeToolCalling(modelId);
    }

    @Override
    public boolean supportsThinkingMode() {
        return client.supportsThinkingMode();
    }

    @Override
    public boolean supportsThinkingMode(String modelId) {
        return client.supportsThinkingMode(modelId);
    }
}
