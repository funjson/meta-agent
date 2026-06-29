package com.funjson.metaagent.provider.domain;

import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.prompt.domain.RenderedPrompt;

/**
 * 描述一次与具体 AI SDK 无关的模型调用请求。
 *
 * @param taskRunId 关联 TaskRun，Control 预处理调用时可为空
 * @param loopNodeId 关联 LoopNode，Control 预处理调用时可为空
 * @param inputSummary 用于审计的非敏感输入摘要
 * @param prompt 已完成版本解析和变量渲染的 Prompt
 * @param maxTokens 最大输出 Token
 * @param tools 本次模型调用允许选择的原生工具
 * @param thinkingMode 本次模型调用的推理/思考模式
 * @param modelId 框架 executor 模型 ID
 */
public record ModelRequest(
        UUID taskRunId,
        UUID loopNodeId,
        String inputSummary,
        RenderedPrompt prompt,
        int maxTokens,
        List<ModelToolSpec> tools,
        ModelThinkingMode thinkingMode,
        String modelId) {

    /**
     * 兼容旧调用点的默认构造器。
     *
     * <p>控制层意图识别、TaskGraph 规划等调用不需要工具能力，默认关闭思考模式，
     * 避免无意增加成本和延迟。</p>
     */
    public ModelRequest(
            UUID taskRunId,
            UUID loopNodeId,
            String inputSummary,
            RenderedPrompt prompt,
            int maxTokens) {
        this(
                taskRunId,
                loopNodeId,
                inputSummary,
                prompt,
                maxTokens,
                List.of(),
                ModelThinkingMode.DISABLED,
                "");
    }

    /**
     * 创建携带工具和思考模式、但不指定模型 ID 的请求。
     */
    public ModelRequest(
            UUID taskRunId,
            UUID loopNodeId,
            String inputSummary,
            RenderedPrompt prompt,
            int maxTokens,
            List<ModelToolSpec> tools,
            ModelThinkingMode thinkingMode) {
        this(
                taskRunId,
                loopNodeId,
                inputSummary,
                prompt,
                maxTokens,
                tools,
                thinkingMode,
                "");
    }

    /**
     * 校验并复制可变集合。
     */
    public ModelRequest {
        if (prompt == null) {
            throw new IllegalArgumentException("Prompt is required");
        }
        tools = tools == null ? List.of() : List.copyOf(tools);
        thinkingMode = thinkingMode == null
                ? ModelThinkingMode.DISABLED
                : thinkingMode;
        modelId = modelId == null ? "" : modelId.trim();
    }
}
