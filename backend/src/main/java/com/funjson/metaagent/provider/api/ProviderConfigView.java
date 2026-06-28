package com.funjson.metaagent.provider.api;

/**
 * Provider 配置的安全 API 视图，不包含密钥内容。
 *
 * @param id Provider ID
 * @param providerType Provider 类型
 * @param displayName 展示名称
 * @param baseUrl Base URL
 * @param modelName 模型名
 * @param enabled 是否启用
 * @param configured 是否已有密钥
 * @param secretSource 密钥来源
 * @param version 乐观锁版本
 */
public record ProviderConfigView(
        String id,
        String providerType,
        String displayName,
        String baseUrl,
        String modelName,
        boolean enabled,
        boolean configured,
        String secretSource,
        long version) {
}
