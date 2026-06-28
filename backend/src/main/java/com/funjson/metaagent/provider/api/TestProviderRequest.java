package com.funjson.metaagent.provider.api;

import jakarta.validation.constraints.Size;

/**
 * Provider 连接测试请求。
 *
 * @param apiKey 可选的本次请求密钥
 */
public record TestProviderRequest(
        @Size(max = 500) String apiKey) {
}
