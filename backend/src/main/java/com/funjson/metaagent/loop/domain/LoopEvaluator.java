package com.funjson.metaagent.loop.domain;


/**
 * 根据结构化 Observation 和执行策略判断完成、调整或失败。
 */
public class LoopEvaluator implements LoopCompletionPolicy {

    private final ClarificationNeedDetector clarificationNeedDetector;

    /**
     * 创建 LoopEvaluator。
     *
     * @param clarificationNeedDetector 澄清需求检测器
     */
    public LoopEvaluator(
            ClarificationNeedDetector clarificationNeedDetector) {
        this.clarificationNeedDetector = clarificationNeedDetector;
    }

    /**
     * 评估一次模型动作结果。
     *
     * @param context 当前节点上下文
     * @param actionResult 动作结果
     * @param policy LoopTree 执行边界
     * @param currentNodeCount 当前节点总数
     * @return Evaluation 结果
     */
    @Override
    public LoopEvaluation evaluate(
            RunExecutionContext context,
            LoopActionResult actionResult,
            LoopExecutionPolicy policy,
            int currentNodeCount) {
        String content = actionResult.content();
        boolean canAdjust = context.depth() < policy.maxDepth()
                && currentNodeCount < policy.maxLoopNodes();
        if (actionResult.actionType() == LoopActionType.CLARIFICATION_REQUEST) {
            if (canAdjust) {
                // 澄清回答是继续执行所需的输入事实，不是最终用户产物。
                return new LoopEvaluation(
                        LoopEvaluationDecision.ADJUST,
                        "已收到用户澄清回答，需要基于补充信息重新执行模型动作",
                        "用户已补充澄清信息："
                                + summarize(content)
                                + "。请结合 Conversation Context 和已解决澄清事实，"
                                + "生成满足原始目标的最终结果。");
            }
            return new LoopEvaluation(
                    LoopEvaluationDecision.FAIL,
                    "已收到用户澄清回答，但 LoopTree 深度或节点预算已耗尽",
                    "Loop execution policy exhausted after clarification");
        }
        if (isUserFacingCompletion(content)) {
            return new LoopEvaluation(
                    LoopEvaluationDecision.COMPLETE,
                    "动作结果满足用户可见产物基础合同",
                    "");
        }

        if (canAdjust) {
            return new LoopEvaluation(
                LoopEvaluationDecision.ADJUST,
                    "动作结果未满足用户可见产物合同，需要调整后重试",
                    feedback(content));
        }
        return new LoopEvaluation(
                LoopEvaluationDecision.FAIL,
                "动作结果未满足合同且已达到 LoopTree 深度或节点预算",
                "Loop execution policy exhausted");
    }

    /**
     * 判断动作结果是否可以作为用户可见完成产物。
     */
    private boolean isUserFacingCompletion(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        if (clarificationNeedDetector.requiresClarification(content)) {
            return false;
        }
        return !content.matches("(?s).*(LoopNode|TaskRun|Control Kernel|Checkpoint|Observation|上下文构建|当前节点).*");
    }

    /**
     * 生成下一轮调整反馈。
     */
    private String feedback(String content) {
        if (content == null || content.isBlank()) {
            return "上一次动作返回空结果；请直接给出可展示的最终结果。";
        }
        if (clarificationNeedDetector.requiresClarification(content)) {
            return "上一次动作是在请求用户补充信息；请改用 clarification.request 动作。";
        }
        return "上一次动作泄露了内部执行术语；请改写成面向用户的自然回复。";
    }

    /**
     * 生成适合放入 Child Loop 反馈的单行摘要。
     *
     * @param content 原始动作内容
     * @return 截断后的摘要
     */
    private String summarize(String content) {
        String normalized = content == null
                ? ""
                : content.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "用户已回答，但内容为空";
        }
        return normalized.length() <= 180
                ? normalized
                : normalized.substring(0, 177) + "...";
    }
}
