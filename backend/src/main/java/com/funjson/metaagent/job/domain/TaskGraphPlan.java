package com.funjson.metaagent.job.domain;

import java.util.List;
import java.util.Optional;

import com.funjson.metaagent.clarification.domain.ClarificationReasonType;
import com.funjson.metaagent.clarification.domain.ClarificationRequestDraft;
import com.funjson.metaagent.clarification.domain.ClarificationSourceType;
import com.funjson.metaagent.task.domain.TaskStatus;

/**
 * 表示 Job 层已验证、可以直接物化的 TaskGraph。
 *
 * @param source 规划来源
 * @param summary 可审计的规划摘要
 * @param nodes 按稳定 sequence 排列的 Task 节点
 * @param clarificationDraft 可选结构化澄清请求草稿
 */
public record TaskGraphPlan(
        String source,
        String summary,
        List<TaskGraphNodePlan> nodes,
        ClarificationRequestDraft clarificationDraft) {

    /**
     * 复制节点集合，防止事务执行期间被调用方修改。
     */
    public TaskGraphPlan {
        nodes = List.copyOf(nodes);
    }

    /**
     * 为普通任务创建单节点 Task Graph。
     *
     * @param goal 用户目标
     * @return 单节点规划
     */
    public static TaskGraphPlan single(String goal) {
        return new TaskGraphPlan(
                "SINGLE_TASK",
                "单任务 TaskGraph：围绕用户目标生成可验收结果。",
                List.of(new TaskGraphNodePlan(
                        "task-1",
                        "完成用户目标",
                        goal,
                        TaskStatus.READY,
                        "LOOP",
                        List.of())),
                null);
    }

    /**
     * 为需要澄清或无法安全拆解的请求创建等待节点。
     *
     * @param goal 用户目标
     * @param source 等待原因来源
     * @param summary 可见等待原因
     * @return WAITING_HUMAN 规划
     */
    public static TaskGraphPlan waiting(
            String goal,
            String source,
            String summary) {
        return waiting(
                goal,
                source,
                summary,
                defaultClarificationQuestion());
    }

    /**
     * 为需要澄清或无法安全拆解的请求创建带指定问题的等待节点。
     *
     * @param goal 用户目标
     * @param source 等待原因来源
     * @param summary 可见等待原因
     * @param question 面向用户的澄清问题
     * @return WAITING_HUMAN 规划
     */
    public static TaskGraphPlan waiting(
            String goal,
            String source,
            String summary,
            String question) {
        return waiting(
                goal,
                source,
                summary,
                question,
                "{}");
    }

    /**
     * 为需要澄清或无法安全拆解的请求创建带指定问题和合同的等待节点。
     *
     * @param goal 用户目标
     * @param source 等待原因来源
     * @param summary 可见等待原因
     * @param question 面向用户的澄清问题
     * @param contractJson 系统用结构化澄清合同
     * @return WAITING_HUMAN 规划
     */
    public static TaskGraphPlan waiting(
            String goal,
            String source,
            String summary,
            String question,
            String contractJson) {
        ClarificationReasonType reasonType = switch (source) {
            case "CLARIFICATION_REQUIRED" ->
                    ClarificationReasonType.GOAL_AMBIGUOUS;
            case "DECOMPOSITION_UNAVAILABLE", "DECOMPOSITION_FAILED" ->
                    ClarificationReasonType.TASK_GRAPH_UNCLEAR;
            default -> ClarificationReasonType.TASK_CONTRACT_MISSING_INPUT;
        };
        return new TaskGraphPlan(
                source,
                summary,
                List.of(new TaskGraphNodePlan(
                        "clarification",
                        "等待补充任务信息",
                        goal,
                        TaskStatus.WAITING_HUMAN,
                        "LOOP",
                        List.of())),
                new ClarificationRequestDraft(
                        ClarificationSourceType.TASK_GRAPH,
                        reasonType,
                        summary,
                        safeQuestion(question),
                        2,
                        safeContract(contractJson)));
    }

    /**
     * 返回 TaskGraph 兜底澄清问题。
     *
     * @return 用户可见的通用澄清问题
     */
    private static String defaultClarificationQuestion() {
        return "我还缺少一些会直接影响结果的信息。"
                + "请补充目标对象、使用场景、关键输入和期望输出；"
                + "如果没有特殊要求，也可以说“按默认处理”。";
    }

    /**
     * 清洗澄清问题，确保落库内容永远是可回答的问题文本。
     *
     * @param question 候选问题
     * @return 非空问题文本
     */
    private static String safeQuestion(String question) {
        // 候选问题为空时回退到统一兜底问题，避免再次出现空泛或不可读文案。
        return question == null || question.isBlank()
                ? defaultClarificationQuestion()
                : question.trim();
    }

    /**
     * 清洗澄清合同 JSON，空值回退为空对象。
     *
     * @param contractJson 候选合同 JSON
     * @return 非空 JSON 对象文本
     */
    private static String safeContract(String contractJson) {
        return contractJson == null || contractJson.isBlank()
                ? "{}"
                : contractJson.trim();
    }

    /**
     * 查询该 TaskGraph 是否包含显式澄清请求。
     *
     * @return 澄清草稿
     */
    public Optional<ClarificationRequestDraft> clarification() {
        return Optional.ofNullable(clarificationDraft);
    }
}
