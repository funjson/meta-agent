package com.funjson.metaagent.loop.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 长任务执行过程中的确定性纠偏策略。
 *
 * <p>它不替代 LoopCompletionPolicy。CompletionPolicy 判断“是否完成”，
 * CorrectionPolicy 判断“下一步动作是否可能让任务漂移、重复或失控”。Web
 * Research 场景下，工具 Observation 不是一律终止工具链：搜索后允许读取和抽取，
 * 抽取后才强制收敛到结果合成。</p>
 */
public class LoopCorrectionPolicy {

    private static final Pattern TOOL_ID_PATTERN =
            Pattern.compile("toolId=([A-Za-z0-9._-]+)");

    /**
     * 判断当前轮次是否仍可暴露任意原生工具。
     *
     * <p>该方法保留给旧调用方；新调用方应优先使用
     * {@link #allowNativeTool(RunExecutionContext, String)} 按 Tool ID 过滤。</p>
     *
     * @param context Loop 执行上下文
     * @return true 表示仍存在可暴露工具；false 表示应只允许模型合成
     */
    public boolean allowNativeTools(RunExecutionContext context) {
        String lastToolId = lastToolId(context.feedback());
        return lastToolId.isBlank() || !"web.extract".equals(lastToolId);
    }

    /**
     * 判断指定 Tool 在当前 Observation 之后是否还允许暴露给模型。
     *
     * @param context Loop 执行上下文
     * @param toolId 候选 Tool ID
     * @return true 表示本轮可暴露该 Tool
     */
    public boolean allowNativeTool(
            RunExecutionContext context,
            String toolId) {
        if (!hasToolObservation(context.feedback())) {
            return true;
        }
        if (toolId == null || toolId.isBlank()) {
            return false;
        }
        String candidate = toolId.trim().toLowerCase(Locale.ROOT);
        String lastToolId = lastToolId(context.feedback());
        if (lastToolId.isBlank()) {
            // 无法识别上一个工具时采用保守策略：禁止继续调用同类不明工具。
            return false;
        }
        if ("web.search".equals(lastToolId)) {
            // 搜索结果只是候选列表，允许下一步读取来源或抽取证据，但不允许再搜同一个方向。
            return "web.fetch".equals(candidate)
                    || "web.extract".equals(candidate)
                    || !candidate.startsWith("web.");
        }
        if ("web.fetch".equals(lastToolId)) {
            // 已读完整来源后只允许进一步抽取证据；再次搜索/读取容易形成循环。
            return "web.extract".equals(candidate)
                    || !candidate.startsWith("web.");
        }
        if ("web.extract".equals(lastToolId)) {
            // 证据已经进入 Evidence Pool，本轮应让模型合成答案。
            return !candidate.startsWith("web.");
        }
        return !lastToolId.equals(candidate);
    }

    /**
     * 对 fallback planner 的计划做确定性纠偏。
     *
     * @param context Loop 执行上下文
     * @param plan 模型或 fallback planner 产生的计划
     * @return 纠偏后的计划
     */
    public LoopPlan correctPlan(
            RunExecutionContext context,
            LoopPlan plan) {
        if (!hasToolObservation(context.feedback())
                || !isToolAction(plan.actionType())) {
            return plan;
        }
        if (allowNativeTool(context, plan.toolId())) {
            return plan;
        }
        // 不允许的重复/回退工具调用收敛到 MODEL_CALL，把已有证据交给模型合成。
        return LoopPlan.modelCall(
                "基于已有工具 Observation 生成面向用户的最终结果",
                "纠偏：已有工具 Observation，阻断重复工具调用并进入结果合成",
                512);
    }

    /**
     * 判断反馈中是否已有工具 Observation。
     */
    private boolean hasToolObservation(String feedback) {
        return feedback != null
                && feedback.contains("上一轮工具动作")
                && feedback.contains("返回");
    }

    /**
     * 从 Evaluation 反馈中恢复上一个工具 ID。
     *
     * @param feedback Loop Evaluation 反馈
     * @return 小写 Tool ID；无法识别时返回空串
     */
    private String lastToolId(String feedback) {
        if (feedback == null || feedback.isBlank()) {
            return "";
        }
        var matcher = TOOL_ID_PATTERN.matcher(feedback);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase(Locale.ROOT);
        }
        String normalized = feedback.toUpperCase(Locale.ROOT);
        if (normalized.contains("WEB_SEARCH")) {
            return "web.search";
        }
        if (normalized.contains("FILE_SEARCH")) {
            return "file.search";
        }
        if (normalized.contains("SKILL_LOAD")) {
            return "skill.load";
        }
        if (normalized.contains("RAG_QUERY")) {
            return "rag.query";
        }
        return "";
    }

    /**
     * @return 是否为工具类动作
     */
    private boolean isToolAction(LoopActionType actionType) {
        return actionType == LoopActionType.TOOL_CALL
                || actionType == LoopActionType.RAG_QUERY
                || actionType == LoopActionType.WEB_SEARCH
                || actionType == LoopActionType.FILE_SEARCH
                || actionType == LoopActionType.SKILL_LOAD;
    }
}
