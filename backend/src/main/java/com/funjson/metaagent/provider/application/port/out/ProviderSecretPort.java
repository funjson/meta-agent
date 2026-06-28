package com.funjson.metaagent.provider.application.port.out;

/**
 * 定义不暴露密钥值的 Provider Secret 访问端口。
 */
public interface ProviderSecretPort {

    /** @return 按优先级解析的密钥 */
    String require(String requestOverride);

    /** 将密钥保存在当前进程内存。 */
    void setMemorySecret(String apiKey);

    /** @return 是否已配置可用密钥 */
    boolean configured();

    /** @return 密钥来源，不包含密钥值 */
    String source();
}
