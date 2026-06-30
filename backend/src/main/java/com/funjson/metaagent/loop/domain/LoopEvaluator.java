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
        if (actionResult.actionType() == LoopActionType.MODEL_CALL
                && isLengthLimited(actionResult)) {
            if (canAdjust) {
                // A length-limited model answer is not a valid user artifact:
                // ask the next LoopNode to produce a complete bounded version.
                return new LoopEvaluation(
                        LoopEvaluationDecision.ADJUST,
                        "模型输出到达 max_tokens 上限，不能作为最终结果",
                        "上一轮模型输出因 finishReason=length 被截断。"
                                + "请基于当前 Conversation Context、工具 Observation "
                                + "和原始目标，生成一份完整但更紧凑的最终版本；"
                                + "不要只续写半截内容，也不要暴露截断或内部执行细节。"
                                + "上一轮已生成内容摘要："
                                + summarize(content, 12_000));
            }
            return new LoopEvaluation(
                    LoopEvaluationDecision.FAIL,
                    "模型输出被截断且 LoopTree 预算已耗尽",
                    "Model output was truncated by max_tokens");
        }
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
        if (isIntermediateToolObservation(actionResult.actionType())) {
            if (canAdjust) {
                String toolId = toolId(actionResult);
                // Tool/RAG/File/Skill 的结果是 Observation，下一轮模型需要基于它生成最终用户产物。
                return new LoopEvaluation(
                        LoopEvaluationDecision.ADJUST,
                        "工具动作已完成，需要基于 Observation 继续生成用户结果",
                        "上一轮工具动作 "
                                + actionResult.actionType().name()
                                + (toolId.isBlank()
                                        ? ""
                                        : "（toolId=" + toolId + "）")
                                + " 返回："
                                + summarize(content, 12_000)
                                + "。请结合 Conversation Context、工具 Observation "
                                + "和原始目标，优先选择 MODEL_CALL 合成面向用户的最终结果；"
                                + "不要重复调用同类工具，除非需要查询一个明确不同的信息缺口。");
            }
            return new LoopEvaluation(
                    LoopEvaluationDecision.FAIL,
                    "工具动作已完成，但 LoopTree 深度或节点预算已耗尽",
                    "Loop execution policy exhausted after tool observation");
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
        if (promisesAnotherToolCall(content)) {
            return false;
        }
        return !content.matches("(?s).*(LoopNode|TaskRun|Control Kernel|Checkpoint|Observation|上下文构建|当前节点).*");
    }

    /**
     * Detects provider-level token-limit stops.
     *
     * @param actionResult model action result
     * @return true if provider stopped because max tokens were reached
     */
    private boolean isLengthLimited(LoopActionResult actionResult) {
        Object finishReason = actionResult.attributes().get("finishReason");
        return finishReason != null
                && "length".equalsIgnoreCase(String.valueOf(finishReason));
    }

    /**
     * 判断模型是否把“后续继续查/继续搜索”当成了最终回复。
     */
    private boolean promisesAnotherToolCall(String content) {
        return content != null
                && content.matches("(?s).*(让我|我来|我会|我将|接下来).{0,12}(重新|再次|再|继续).{0,12}(搜索|查找|查询|检索).*");
    }

    /**
     * Tool 类动作默认不是用户最终产物，而是 ReAct 的 Observation。
     */
    private boolean isIntermediateToolObservation(
            LoopActionType actionType) {
        return actionType == LoopActionType.TOOL_CALL
                || actionType == LoopActionType.RAG_QUERY
                || actionType == LoopActionType.WEB_SEARCH
                || actionType == LoopActionType.FILE_SEARCH
                || actionType == LoopActionType.SKILL_LOAD;
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
        if (promisesAnotherToolCall(content)) {
            return "上一次回答承诺继续搜索或查询，但当前应基于已有工具 Observation 直接合成结果；"
                    + "请说明已有信息的局限，并给出当前能可靠回答的内容，不要承诺后续工具调用。";
        }
        return "上一次动作泄露了内部执行术语；请改写成面向用户的自然回复。";
    }

    /**
     * 从工具 Observation 属性中读取 Tool ID。
     *
     * @param actionResult 动作结果
     * @return Tool ID；不存在时返回空串
     */
    private String toolId(LoopActionResult actionResult) {
        Object value = actionResult.attributes().get("toolId");
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 生成适合放入 Child Loop 反馈的单行摘要。
     *
     * @param content 原始动作内容
     * @return 截断后的摘要
     */
    private String summarize(String content) {
        return summarize(content, 180);
    }

    /**
     * 生成指定长度的单行摘要。
     *
     * @param content 原始动作内容
     * @param maxLength 最大长度
     * @return 截断后的摘要
     */
    private String summarize(String content, int maxLength) {
        String normalized = content == null
                ? ""
                : content.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "用户已回答，但内容为空";
        }
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, Math.max(0, maxLength - 3))
                        + "...";
    }
}
