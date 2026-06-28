package com.funjson.metaagent.loop.domain;

import java.util.Map;

import com.funjson.metaagent.provider.domain.ModelResponse;
import com.funjson.metaagent.runtime.domain.ChildJobOutcome;
import com.funjson.metaagent.runtime.domain.ClarificationAnswerOutcome;
import com.funjson.metaagent.tool.domain.ToolResult;

/**
 * 模型、工具或 Child Job 动作返回给 Loop Evaluation 的统一 Observation。
 *
 * @param actionType 动作类型
 * @param source 动作来源
 * @param content 可供 Evaluation 使用的结果内容
 * @param attributes 结构化审计属性
 */
public record LoopActionResult(
        LoopActionType actionType,
        String source,
        String content,
        Map<String, Object> attributes) {

    /**
     * 从模型响应创建动作结果。
     *
     * @param response 模型响应
     * @return 动作结果
     */
    public static LoopActionResult fromModel(ModelResponse response) {
        return new LoopActionResult(
                LoopActionType.MODEL_CALL,
                response.provider() + "/" + response.model(),
                response.content(),
                Map.of(
                        "provider", response.provider(),
                        "model", response.model(),
                        "finishReason", response.finishReason()));
    }

    /**
     * 从 ChildJobOutcome 创建动作结果。
     *
     * @param outcome Child Job 结果
     * @return 动作结果
     */
    public static LoopActionResult fromChildJob(
            ChildJobOutcome outcome) {
        return new LoopActionResult(
                LoopActionType.CHILD_JOB,
                "child-job:" + outcome.childJobId(),
                outcome.resultSummary(),
                Map.of(
                        "childJobId", outcome.childJobId(),
                        "status", outcome.status(),
                        "evidenceCount", outcome.evidenceCount(),
                        "outputs", outcome.outputs()));
    }

    /**
     * 从 ToolResult 创建动作结果。
     *
     * @param result 工具结果
     * @return 动作结果
     */
    public static LoopActionResult fromTool(ToolResult result) {
        return new LoopActionResult(
                LoopActionType.TOOL_CALL,
                "tool:" + result.invocationId(),
                result.content(),
                result.attributes());
    }

    /**
     * 从用户澄清回答创建动作结果。
     *
     * @param outcome 澄清回答结果
     * @return 动作结果
     */
    public static LoopActionResult fromClarification(
            ClarificationAnswerOutcome outcome) {
        return new LoopActionResult(
                LoopActionType.CLARIFICATION_REQUEST,
                "clarification:" + outcome.clarificationRequestId(),
                outcome.answer(),
                Map.of(
                        "clarificationRequestId",
                        outcome.clarificationRequestId(),
                        "question",
                        outcome.question()));
    }

    /**
     * 校验并复制动作结果。
     */
    public LoopActionResult {
        if (actionType == null) {
            throw new IllegalArgumentException("Action type is required");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Action source is required");
        }
        attributes = attributes == null
                ? Map.of()
                : Map.copyOf(attributes);
    }
}
