package com.funjson.metaagent.loop.domain;

import java.util.Map;

/**
 * Planning 阶段生成的结构化动作计划。
 *
 * @param actionType 动作类型
 * @param completionCriterion 完成判据
 * @param summary 可展示的计划摘要
 * @param maxTokens 模型输出预算
 * @param derivationRequest Child Loop 或 Child Job 的结构化派生请求
 * @param toolId 可选 Tool ID
 * @param toolArguments 可选 Tool 参数
 */
public record LoopPlan(
        LoopActionType actionType,
        String completionCriterion,
        String summary,
        int maxTokens,
        ExecutionDerivationRequest derivationRequest,
        String toolId,
        Map<String, Object> toolArguments) {

    /**
     * 创建模型动作计划。
     *
     * @param completionCriterion 完成判据
     * @param summary 计划摘要
     * @param maxTokens 模型输出预算
     * @return 模型动作计划
     */
    public static LoopPlan modelCall(
            String completionCriterion,
            String summary,
            int maxTokens) {
        return new LoopPlan(
                LoopActionType.MODEL_CALL,
                completionCriterion,
                summary,
                maxTokens,
                null,
                null,
                Map.of());
    }

    /**
     * 创建 Tool 类动作计划。
     *
     * @param actionType Tool 类动作类型
     * @param completionCriterion 完成判据
     * @param summary 计划摘要
     * @param toolId Tool ID
     * @param toolArguments Tool 参数
     * @return Tool 动作计划
     */
    public static LoopPlan toolCall(
            LoopActionType actionType,
            String completionCriterion,
            String summary,
            String toolId,
            Map<String, Object> toolArguments) {
        return new LoopPlan(
                actionType,
                completionCriterion,
                summary,
                0,
                null,
                toolId,
                toolArguments);
    }

    /**
     * 创建 Child Job 派生动作计划。
     *
     * @param completionCriterion 完成判据
     * @param summary 计划摘要
     * @param derivationRequest Child Job 派生请求
     * @return Child Job 动作计划
     */
    public static LoopPlan childJob(
            String completionCriterion,
            String summary,
            ExecutionDerivationRequest derivationRequest) {
        return new LoopPlan(
                LoopActionType.CHILD_JOB,
                completionCriterion,
                summary,
                0,
                derivationRequest,
                null,
                Map.of());
    }

    /**
     * 创建 Child LoopNode 派生动作计划。
     *
     * @param completionCriterion 完成判据
     * @param summary 计划摘要
     * @param derivationRequest Child Loop 派生请求
     * @return Child Loop 动作计划
     */
    public static LoopPlan childLoop(
            String completionCriterion,
            String summary,
            ExecutionDerivationRequest derivationRequest) {
        return new LoopPlan(
                LoopActionType.CHILD_LOOP,
                completionCriterion,
                summary,
                0,
                derivationRequest,
                null,
                Map.of());
    }

    /**
     * 校验结构化计划，并复制可变参数 Map。
     */
    public LoopPlan {
        if (actionType == null) {
            throw new IllegalArgumentException("Action type is required");
        }
        if (completionCriterion == null
                || completionCriterion.isBlank()) {
            throw new IllegalArgumentException(
                    "Completion criterion is required");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("Plan summary is required");
        }
        toolArguments = toolArguments == null
                ? Map.of()
                : Map.copyOf(toolArguments);
        if (actionType == LoopActionType.MODEL_CALL && maxTokens < 1) {
            throw new IllegalArgumentException(
                    "Model action requires a positive token budget");
        }
        if (isToolAction(actionType)
                && (toolId == null || toolId.isBlank())) {
            throw new IllegalArgumentException(
                    "Tool action requires a tool id");
        }
        if (actionType == LoopActionType.CHILD_JOB
                && (derivationRequest == null
                || derivationRequest.type()
                != ExecutionDerivationType.CHILD_JOB)) {
            throw new IllegalArgumentException(
                    "Child Job action requires a child-job derivation");
        }
        if (actionType == LoopActionType.CHILD_LOOP
                && (derivationRequest == null
                || derivationRequest.type()
                != ExecutionDerivationType.CHILD_LOOP)) {
            throw new IllegalArgumentException(
                    "Child Loop action requires a child derivation");
        }
    }

    /** @return 是否为 Tool Runtime 可执行动作。 */
    private static boolean isToolAction(LoopActionType actionType) {
        return actionType == LoopActionType.TOOL_CALL
                || actionType == LoopActionType.RAG_QUERY
                || actionType == LoopActionType.WEB_SEARCH
                || actionType == LoopActionType.FILE_SEARCH
                || actionType == LoopActionType.SKILL_LOAD
                || actionType == LoopActionType.CLARIFICATION_REQUEST;
    }
}
