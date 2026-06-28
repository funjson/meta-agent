package com.funjson.metaagent.job.application;

import com.funjson.metaagent.job.application.port.out.TaskGraphPlanningPort;
import com.funjson.metaagent.job.domain.TaskGraphNodePlan;
import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.job.domain.TaskGraphPlanningRequest;
import com.funjson.metaagent.job.domain.TaskGraphValidator;
import org.springframework.stereotype.Service;

/**
 * 根据意图结果选择单任务、澄清等待或模型复合任务拆解策略。
 */
@Service
public class TaskGraphPlanner {

    private final TaskGraphPlanningPort planningPort;
    private final TaskGraphValidator validator;

    /**
     * 创建 Job TaskGraph Planner。
     *
     * @param planningPort 外部智能规划端口
     * @param validator Task Graph 不变量校验器
     */
    public TaskGraphPlanner(
            TaskGraphPlanningPort planningPort,
            TaskGraphValidator validator) {
        this.planningPort = planningPort;
        this.validator = validator;
    }

    /**
     * 形成可以直接持久化和调度的 Task Graph。
     *
     * @param request 规划请求
     * @return 已验证规划
     */
    public TaskGraphPlan plan(TaskGraphPlanningRequest request) {
        if (request.requiresClarification()) {
            return validator.validate(TaskGraphPlan.waiting(
                    request.goalSummary(),
                    "CLARIFICATION_REQUIRED",
                    "当前请求存在会实质影响执行结果的缺失信息，等待用户补充。",
                    clarificationQuestion(request),
                    request.clarificationContractJson()));
        }
        if (!request.compoundTask()) {
            return validator.validate(
                    TaskGraphPlan.single(request.originalRequest()));
        }
        if (!request.modelPlanningAllowed()) {
            return validator.validate(TaskGraphPlan.waiting(
                    request.goalSummary(),
                    "DECOMPOSITION_UNAVAILABLE",
                    "已识别为复合任务，但当前没有可用的结构化拆解能力。"));
        }

        try {
            return planningPort.plan(request)
                    .map(plan -> enrichWithJobContext(
                            plan,
                            request))
                    .map(validator::validate)
                    .orElseGet(() -> decompositionFailure(request));
        } catch (RuntimeException exception) {
            // 外部候选图只有通过 Job 层调度不变量校验后才能落库。
            return decompositionFailure(request);
        }
    }

    /**
     * 创建复合任务拆解失败后的安全等待规划。
     *
     * @param request 原规划请求
     * @return WAITING_HUMAN Task Graph
     */
    private TaskGraphPlan decompositionFailure(
            TaskGraphPlanningRequest request) {
        return validator.validate(TaskGraphPlan.waiting(
                request.goalSummary(),
                "DECOMPOSITION_FAILED",
                "复合任务拆解未通过结构校验，等待用户补充或重试。",
                "我已经识别到这是一个复合任务，但还无法稳定拆解执行步骤。"
                        + "请补充任务边界、优先级、关键输入和期望产物，"
                        + "我会据此重新规划 TaskGraph。"));
    }

    /**
     * 生成 TaskGraph 规划阶段的用户可见澄清问题。
     *
     * @param request 规划请求
     * @return 面向用户、可直接回答的问题
     */
    private String clarificationQuestion(TaskGraphPlanningRequest request) {
        if (!request.clarificationQuestion().isBlank()) {
            return request.clarificationQuestion();
        }

        String constraints = request.constraints().isEmpty()
                ? "目前没有识别到明确约束"
                : "已识别约束：" + String.join("；", request.constraints());
        // 澄清问题必须把缺失原因带给用户，而不是只暴露框架级等待状态。
        return """
                我还缺少一些会直接影响结果的信息。
                目标：%s
                %s。
                请补充目标对象/背景、使用场景、必须包含或避免的内容，以及期望输出形式、长度或风格；如果希望我按默认假设推进，也可以直接说明。
                """.formatted(
                        request.goalSummary(),
                        constraints).trim();
    }

    /**
     * 把稳定 Job 目标和约束注入每个 Task，避免依赖模型自行复制关键上下文。
     *
     * @param plan 模型候选图
     * @param request 原规划请求
     * @return 上下文完整的 Task Graph
     */
    private TaskGraphPlan enrichWithJobContext(
            TaskGraphPlan plan,
            TaskGraphPlanningRequest request) {
        String constraints = request.constraints().isEmpty()
                ? "无额外约束"
                : String.join("；", request.constraints());
        return new TaskGraphPlan(
                plan.source(),
                plan.summary(),
                plan.nodes().stream()
                        .map(node -> new TaskGraphNodePlan(
                                node.key(),
                                node.title(),
                                """
                                当前 Task 目标：
                                %s

                                Job 原始请求：
                                %s

                                Job 目标摘要：
                                %s

                                用户约束：
                                %s
                                """.formatted(
                                        node.goal(),
                                        request.originalRequest(),
                                        request.goalSummary(),
                                        constraints).trim(),
                                node.initialStatus(),
                                node.executionMode(),
                                node.dependsOnKeys()))
                        .toList(),
                plan.clarificationDraft());
    }
}
