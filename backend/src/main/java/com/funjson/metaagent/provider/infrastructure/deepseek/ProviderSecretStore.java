package com.funjson.metaagent.provider.infrastructure.deepseek;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.funjson.metaagent.provider.application.port.out.ProviderSecretPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 在不持久化和不回传密钥的前提下解析 Provider API Key。
 */
@Component
public class ProviderSecretStore implements ProviderSecretPort {

    private final Map<String, String> environmentSecrets;
    private final Map<String, String> memorySecrets = new ConcurrentHashMap<>();

    /**
     * 创建密钥 Store。
     *
     * @param deepSeekEnvironmentSecret DeepSeek 环境变量密钥
     * @param glmEnvironmentSecret GLM 环境变量密钥
     */
    @Autowired
    public ProviderSecretStore(
            @Value("${meta-agent.provider.deepseek.api-key:}")
            String deepSeekEnvironmentSecret,
            @Value("${meta-agent.provider.glm.api-key:}")
            String glmEnvironmentSecret) {
        Map<String, String> secrets = new HashMap<>();
        secrets.put("deepseek", normalize(deepSeekEnvironmentSecret));
        secrets.put("glm", normalize(glmEnvironmentSecret));
        this.environmentSecrets = Map.copyOf(
                secrets.entrySet().stream()
                        .filter(entry -> entry.getValue() != null)
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue)));
    }

    /**
     * 测试兼容构造器。
     *
     * @param deepSeekEnvironmentSecret DeepSeek 环境变量密钥
     */
    public ProviderSecretStore(String deepSeekEnvironmentSecret) {
        this(deepSeekEnvironmentSecret, "");
    }

    /**
     * 按请求、内存、环境变量顺序解析密钥。
     *
     * @param providerId Provider ID
     * @param requestOverride 请求级密钥
     * @return 密钥
     */
    public String require(String providerId, String requestOverride) {
        String override = normalize(requestOverride);
        if (override != null) {
            return override;
        }
        String inMemory = memorySecrets.get(providerId);
        if (inMemory != null) {
            return inMemory;
        }
        String environmentSecret = environmentSecrets.get(providerId);
        if (environmentSecret != null) {
            return environmentSecret;
        }
        throw new IllegalStateException(providerId + " API key is not configured");
    }

    /**
     * 将密钥仅保存在当前进程内存。
     *
     * @param providerId Provider ID
     * @param apiKey API Key
     */
    public void setMemorySecret(String providerId, String apiKey) {
        String normalized = normalize(apiKey);
        if (normalized == null) {
            throw new IllegalArgumentException("API key must not be blank");
        }
        memorySecrets.put(providerId, normalized);
    }

    /**
     * 判断是否存在可用密钥。
     *
     * @return 是否已配置
     */
    public boolean configured(String providerId) {
        return memorySecrets.containsKey(providerId)
                || environmentSecrets.get(providerId) != null;
    }

    /**
     * 返回密钥来源，不返回密钥值。
     *
     * @return 来源
     */
    public String source(String providerId) {
        if (memorySecrets.containsKey(providerId)) {
            return "MEMORY";
        }
        return environmentSecrets.get(providerId) != null
                ? "ENVIRONMENT"
                : "NONE";
    }

    /**
     * 规范化密钥输入。
     *
     * @param value 原始值
     * @return 规范值或空
     */
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
