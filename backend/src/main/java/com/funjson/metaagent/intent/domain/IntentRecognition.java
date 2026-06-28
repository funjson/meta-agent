package com.funjson.metaagent.intent.domain;

import java.util.List;

/**
 * 表示经过规则、模型和校验后的结构化意图识别结果。
 *
 * @param intentType 意图类型
 * @param confidence 置信度，范围为 0 到 1
 * @param classifier 实际产生结果的分类器
 * @param goalSummary 任务目标摘要
 * @param decisionSummary 可审计但不包含隐藏思维的决策摘要
 * @param constraints 用户明确表达的约束
 * @param requiresClarification 是否需要先向用户澄清
 * @param compoundTask 是否可能需要拆分为多个 Task
 * @param riskLevel 初步风险等级
 * @param labels 供 Job 层匹配 TaskGraphTemplate 的任务标签
 * @param clarificationQuestion 需要澄清时的用户可见自然问题
 * @param clarificationContractJson 需要澄清时的系统用结构化合同 JSON
 */
public record IntentRecognition(
        IntentType intentType,
        double confidence,
        String classifier,
        String goalSummary,
        String decisionSummary,
        List<String> constraints,
        boolean requiresClarification,
        boolean compoundTask,
        IntentRiskLevel riskLevel,
        List<String> labels,
        String clarificationQuestion,
        String clarificationContractJson) {

    /**
     * 兼容旧调用的意图构造器。
     */
    public IntentRecognition(
            IntentType intentType,
            double confidence,
            String classifier,
            String goalSummary,
            String decisionSummary,
            List<String> constraints,
            boolean requiresClarification,
            boolean compoundTask,
            IntentRiskLevel riskLevel,
            List<String> labels) {
        this(
                intentType,
                confidence,
                classifier,
                goalSummary,
                decisionSummary,
                constraints,
                requiresClarification,
                compoundTask,
                riskLevel,
                labels,
                "",
                "{}");
    }

    /**
     * 复制意图集合字段，保证识别结果不可变。
     */
    public IntentRecognition {
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        labels = labels == null ? List.of() : List.copyOf(labels);
        clarificationQuestion = clarificationQuestion == null
                ? ""
                : clarificationQuestion.trim();
        clarificationContractJson = clarificationContractJson == null
                || clarificationContractJson.isBlank()
                        ? "{}"
                        : clarificationContractJson.trim();
    }

    /**
     * 判断该意图是否要求创建 Job。
     *
     * @return 问答、创建和修改类任务都返回 {@code true}
     */
    public boolean createsJob() {
        return intentType == IntentType.CREATE_JOB
                || intentType == IntentType.CHAT_QA
                || intentType == IntentType.MODIFY_CONSTRAINTS;
    }
}
