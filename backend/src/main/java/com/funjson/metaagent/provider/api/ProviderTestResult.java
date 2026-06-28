package com.funjson.metaagent.provider.api;

/**
 * Provider 连接测试结果。
 *
 * @param success 是否成功
 * @param provider Provider ID
 * @param model 模型名
 * @param latencyMs 延迟
 * @param message 结果消息
 */
public record ProviderTestResult(
        boolean success,
        String provider,
        String model,
        long latencyMs,
        String message) {
}
