package com.funjson.metaagent.provider.domain;

/**
 * 模型推理/思考模式的框架级开关。
 *
 * <p>不同 Provider 的参数名称不同，例如 DeepSeek 暴露 reasoning_content，
 * GLM 暴露 thinking 配置。Loop Kernel 只表达“是否允许深度思考”，具体
 * HTTP 参数由 Provider Adapter 负责映射。</p>
 */
public enum ModelThinkingMode {

    /** 默认关闭，优先保证成本、延迟和结果稳定性。 */
    DISABLED,
    /** 交给 Provider 或模型根据任务复杂度自动决定。 */
    AUTO,
    /** 明确请求模型启用推理/深度思考能力。 */
    ENABLED
}
