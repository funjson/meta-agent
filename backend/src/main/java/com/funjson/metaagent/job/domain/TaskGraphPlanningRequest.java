package com.funjson.metaagent.job.domain;

import java.util.List;

/**
 * 描述 Job TaskGraph 规划所需的最小输入。
 *
 * @param originalRequest 原始用户请求
 * @param goalSummary 意图识别后的目标摘要
 * @param constraints 用户明确约束
 * @param clarificationQuestion 意图阶段建议的用户可见澄清问题
 * @param clarificationContractJson 意图阶段输出的结构化澄清合同
 * @param requiresClarification 是否需要澄清
 * @param compoundTask 是否为复合任务
 * @param modelPlanningAllowed 是否允许调用真实模型拆解
 */
public record TaskGraphPlanningRequest(
        String originalRequest,
        String goalSummary,
        List<String> constraints,
        String clarificationQuestion,
        String clarificationContractJson,
        boolean requiresClarification,
        boolean compoundTask,
        boolean modelPlanningAllowed) {

    /**
     * 兼容旧调用的规划请求构造器。
     */
    public TaskGraphPlanningRequest(
            String originalRequest,
            String goalSummary,
            List<String> constraints,
            String clarificationQuestion,
            boolean requiresClarification,
            boolean compoundTask,
            boolean modelPlanningAllowed) {
        this(
                originalRequest,
                goalSummary,
                constraints,
                clarificationQuestion,
                "{}",
                requiresClarification,
                compoundTask,
                modelPlanningAllowed);
    }

    /**
     * 复制约束集合，保持规划请求不可变。
     */
    public TaskGraphPlanningRequest {
        constraints = List.copyOf(constraints);
        clarificationQuestion = clarificationQuestion == null
                ? ""
                : clarificationQuestion.trim();
        clarificationContractJson = clarificationContractJson == null
                || clarificationContractJson.isBlank()
                        ? "{}"
                        : clarificationContractJson.trim();
    }
}
