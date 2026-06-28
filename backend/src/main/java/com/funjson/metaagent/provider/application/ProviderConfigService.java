package com.funjson.metaagent.provider.application;

import java.net.URI;
import java.util.List;

import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.provider.api.ProviderConfigView;
import com.funjson.metaagent.provider.api.UpdateProviderConfigRequest;
import com.funjson.metaagent.provider.application.port.out.ProviderConfigStore;
import com.funjson.metaagent.provider.application.port.out.ProviderSecretPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理 Provider 配置和安全密钥解析。
 */
@Service
public class ProviderConfigService {

    private final ProviderConfigStore repository;
    private final ProviderSecretPort secretStore;

    /**
     * 创建 Provider 配置服务。
     *
     * @param repository Provider 配置 Repository
     * @param secretStore 密钥 Store
     */
    public ProviderConfigService(
            ProviderConfigStore repository,
            ProviderSecretPort secretStore) {
        this.repository = repository;
        this.secretStore = secretStore;
    }

    /**
     * 查询安全 Provider 配置视图。
     *
     * @return 配置列表
     */
    @Transactional(readOnly = true)
    public List<ProviderConfigView> list() {
        return repository.findAll().stream().map(this::toView).toList();
    }

    /**
     * 获取可用 DeepSeek 配置。
     *
     * @return DeepSeek 配置
     */
    @Transactional(readOnly = true)
    public ProviderConfigStore.ProviderConfig requireDeepSeek() {
        ProviderConfigStore.ProviderConfig config = repository.find("deepseek");
        if (!config.enabled()) {
            throw new RuntimeStateException(
                    "PROVIDER_DISABLED",
                    "DeepSeek provider is disabled");
        }
        return config;
    }

    /**
     * 更新 Provider 配置和可选内存密钥。
     *
     * @param id Provider ID
     * @param request 更新请求
     * @return 更新后视图
     */
    @Transactional
    public ProviderConfigView update(
            String id,
            UpdateProviderConfigRequest request) {
        if (request.persistSecret() && request.apiKey() != null) {
            throw new RuntimeStateException(
                    "SECRET_PERSISTENCE_UNAVAILABLE",
                    "Persistent secret storage requires META_AGENT_MASTER_KEY and is not enabled");
        }
        validateBaseUrl(request.baseUrl());
        String secretSource = secretStore.source();
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            secretStore.setMemorySecret(request.apiKey());
            secretSource = "MEMORY";
        }
        boolean updated = repository.update(
                id,
                stripTrailingSlash(request.baseUrl()),
                request.modelName().trim(),
                secretSource,
                request.enabled(),
                request.expectedVersion());
        if (!updated) {
            throw new RuntimeStateException(
                    "VERSION_CONFLICT",
                    "Provider configuration has changed; refresh and retry");
        }
        return toView(repository.find(id));
    }

    /**
     * 按请求、内存、环境变量顺序解析密钥。
     *
     * @param requestOverride 请求级密钥
     * @return 实际密钥
     */
    public String resolveSecret(String requestOverride) {
        return secretStore.require(requestOverride);
    }

    /**
     * 转换为不含密钥值的 API 视图。
     *
     * @param config 配置记录
     * @return 安全视图
     */
    private ProviderConfigView toView(
            ProviderConfigStore.ProviderConfig config) {
        return new ProviderConfigView(
                config.id(),
                config.providerType(),
                config.displayName(),
                config.baseUrl(),
                config.modelName(),
                config.enabled(),
                secretStore.configured(),
                secretStore.source(),
                config.version());
    }

    /**
     * 限制 Provider 地址为绝对 HTTPS URL。
     *
     * @param value 地址
     */
    private void validateBaseUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new RuntimeStateException("INVALID_PROVIDER_URL", "Invalid provider base URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new RuntimeStateException(
                    "INVALID_PROVIDER_URL",
                    "Provider base URL must be an absolute HTTPS URL");
        }
    }

    /**
     * 规范化 Base URL。
     *
     * @param value 原始地址
     * @return 无末尾斜杠地址
     */
    private String stripTrailingSlash(String value) {
        return value.trim().replaceAll("/+$", "");
    }
}
