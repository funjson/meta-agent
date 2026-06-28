package com.funjson.metaagent.provider.infrastructure.deepseek;

import java.util.concurrent.atomic.AtomicReference;

import com.funjson.metaagent.provider.application.port.out.ProviderSecretPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 在不持久化和不回传密钥的前提下解析 DeepSeek API Key。
 */
@Component
public class ProviderSecretStore implements ProviderSecretPort {

    private final String environmentSecret;
    private final AtomicReference<String> memorySecret = new AtomicReference<>();

    /**
     * 创建密钥 Store。
     *
     * @param environmentSecret 环境变量密钥
     */
    public ProviderSecretStore(
            @Value("${meta-agent.provider.deepseek.api-key:}") String environmentSecret) {
        this.environmentSecret = normalize(environmentSecret);
    }

    /**
     * 按请求、内存、环境变量顺序解析密钥。
     *
     * @param requestOverride 请求级密钥
     * @return 密钥
     */
    public String require(String requestOverride) {
        String override = normalize(requestOverride);
        if (override != null) {
            return override;
        }
        String inMemory = memorySecret.get();
        if (inMemory != null) {
            return inMemory;
        }
        if (environmentSecret != null) {
            return environmentSecret;
        }
        throw new IllegalStateException("DeepSeek API key is not configured");
    }

    /**
     * 将密钥仅保存在当前进程内存。
     *
     * @param apiKey API Key
     */
    public void setMemorySecret(String apiKey) {
        String normalized = normalize(apiKey);
        if (normalized == null) {
            throw new IllegalArgumentException("API key must not be blank");
        }
        memorySecret.set(normalized);
    }

    /**
     * 判断是否存在可用密钥。
     *
     * @return 是否已配置
     */
    public boolean configured() {
        return memorySecret.get() != null || environmentSecret != null;
    }

    /**
     * 返回密钥来源，不返回密钥值。
     *
     * @return 来源
     */
    public String source() {
        if (memorySecret.get() != null) {
            return "MEMORY";
        }
        return environmentSecret != null ? "ENVIRONMENT" : "NONE";
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
