package com.funjson.metaagent.provider.domain;

/**
 * AI 模型能力的框架无关端口。
 *
 * <p>未来接入 Spring AI 时新增 Adapter 实现该端口，Control 和 Loop 不直接依赖
 * Spring AI 类型。</p>
 */
public interface ModelProvider {

    /**
     * 返回稳定 Provider ID。
     *
     * @return Provider ID
     */
    String providerId();

    /**
     * 执行模型调用。
     *
     * @param request 模型请求
     * @return 模型响应
     */
    ModelResponse generate(ModelRequest request);
}
