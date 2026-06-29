package com.funjson.metaagent.provider.application.port.out;

import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;

/**
 * 定义使用请求级临时密钥测试 Provider 连接的端口。
 */
public interface ProviderConnectionTestPort {

    /** @return Provider ID */
    String providerId();

    /** @return Provider 测试响应 */
    ModelResponse generate(
            ModelRequest request,
            String requestSecretOverride);
}
