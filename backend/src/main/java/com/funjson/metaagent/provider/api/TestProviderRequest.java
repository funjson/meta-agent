package com.funjson.metaagent.provider.api;

import jakarta.validation.constraints.Size;

/**
 * Provider 连接测试请求。
 *
 * @param apiKey 可选的本次请求密钥
 * @param modelId 可选的框架模型 ID
 */
public record TestProviderRequest(
        @Size(max = 500) String apiKey,
        @Size(max = 120) String modelId) {
}
