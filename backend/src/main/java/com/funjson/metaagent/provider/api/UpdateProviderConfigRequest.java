package com.funjson.metaagent.provider.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 更新 Provider 配置请求。
 *
 * @param baseUrl Base URL
 * @param modelName 模型名
 * @param apiKey 可选新密钥
 * @param persistSecret 是否要求持久化密钥
 * @param enabled 是否启用
 * @param expectedVersion 期望版本
 */
public record UpdateProviderConfigRequest(
        @NotBlank @Size(max = 500) String baseUrl,
        @NotBlank @Size(max = 120) String modelName,
        @Size(max = 500) String apiKey,
        boolean persistSecret,
        boolean enabled,
        @NotNull @Min(0) Long expectedVersion) {
}
