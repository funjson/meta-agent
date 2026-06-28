package com.funjson.metaagent.provider.application;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.funjson.metaagent.provider.domain.ModelProvider;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * 按稳定 ID 管理所有模型 Provider Adapter。
 */
@Component
public class ModelProviderRegistry {

    private final Map<String, ModelProvider> providers;

    /**
     * 创建不可变 Provider 索引。
     *
     * @param providers Spring 发现的 Provider
     */
    public ModelProviderRegistry(List<ModelProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ModelProvider::providerId,
                        Function.identity()));
    }

    /**
     * 获取指定 Provider。
     *
     * @param providerId Provider ID
     * @return Provider
     */
    public ModelProvider require(String providerId) {
        ModelProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown model provider: " + providerId);
        }
        return provider;
    }
}
