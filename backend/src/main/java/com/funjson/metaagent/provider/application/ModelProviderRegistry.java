package com.funjson.metaagent.provider.application;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.funjson.metaagent.provider.domain.ModelProvider;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 按稳定 ID 管理所有模型 Provider Adapter。
 */
@Component
public class ModelProviderRegistry {

    private final Map<String, ModelProvider> providers;
    private final ModelCatalogService modelCatalog;

    /**
     * 创建不可变 Provider 索引。
     *
     * @param providers Spring 发现的 Provider
     * @param modelCatalog 模型目录
     */
    @Autowired
    public ModelProviderRegistry(
            List<ModelProvider> providers,
            ModelCatalogService modelCatalog) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ModelProvider::providerId,
                        Function.identity()));
        this.modelCatalog = modelCatalog;
    }

    /**
     * 测试兼容构造器。
     *
     * @param providers Spring 发现的 Provider
     */
    public ModelProviderRegistry(List<ModelProvider> providers) {
        this(providers, new ModelCatalogService());
    }

    /**
     * 获取指定 Provider。
     *
     * @param providerId Provider ID
     * @return Provider
     */
    public ModelProvider require(String providerId) {
        String resolvedProviderId = providers.containsKey(providerId)
                ? providerId
                : modelCatalog.find(providerId)
                        .map(model -> model.providerId())
                        .orElse(providerId);
        ModelProvider provider = providers.get(resolvedProviderId);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown model provider: " + providerId);
        }
        return provider;
    }
}
