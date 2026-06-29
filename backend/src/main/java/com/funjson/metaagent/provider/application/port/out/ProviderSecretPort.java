package com.funjson.metaagent.provider.application.port.out;

/**
 * 定义不暴露密钥值的 Provider Secret 访问端口。
 */
public interface ProviderSecretPort {

    /** @return 按优先级解析的密钥 */
    String require(String providerId, String requestOverride);

    /** @return 兼容旧调用点的 DeepSeek 密钥 */
    default String require(String requestOverride) {
        return require("deepseek", requestOverride);
    }

    /** 将密钥保存在当前进程内存。 */
    void setMemorySecret(String providerId, String apiKey);

    /** 将 DeepSeek 密钥保存在当前进程内存。 */
    default void setMemorySecret(String apiKey) {
        setMemorySecret("deepseek", apiKey);
    }

    /** @return 是否已配置可用密钥 */
    boolean configured(String providerId);

    /** @return DeepSeek 是否已配置可用密钥 */
    default boolean configured() {
        return configured("deepseek");
    }

    /** @return 密钥来源，不包含密钥值 */
    String source(String providerId);

    /** @return DeepSeek 密钥来源，不包含密钥值 */
    default String source() {
        return source("deepseek");
    }
}
