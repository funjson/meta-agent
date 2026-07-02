package com.funjson.metaagent.loop.domain;

/**
 * Evaluates a LoopNode action result and decides whether the current LoopNode
 * is complete, needs another adjustment node, or should fail.
 *
 * <p>The evaluation order is intentional:</p>
 * <ol>
 *     <li>Protocol-level hard guards, such as token truncation and unresolved tool calls.</li>
 *     <li>Model-driven semantic completion judgment.</li>
 *     <li>Rule-based fallback when the model judge is unavailable or inconclusive.</li>
 * </ol>
 */
public class LoopEvaluator implements LoopCompletionPolicy {

    private final ClarificationNeedDetector clarificationNeedDetector;
    private final LoopCompletionJudge completionJudge;

    /**
     * Creates a Loop evaluator.
     *
     * @param clarificationNeedDetector detector for natural-language clarification requests
     * @param completionJudge model or test implementation of semantic completion judgment
     */
    public LoopEvaluator(
            ClarificationNeedDetector clarificationNeedDetector,
            LoopCompletionJudge completionJudge) {
        this.clarificationNeedDetector = clarificationNeedDetector;
        this.completionJudge = completionJudge == null
                ? LoopCompletionJudge.noOp()
                : completionJudge;
    }

    /**
     * Evaluates one action result against the current Loop execution budget.
     *
     * @param context current LoopNode context
     * @param actionResult action result returned by model/tool/child job/clarification
     * @param policy Loop execution budget
     * @param currentNodeCount current LoopTree node count
     * @return structured Loop evaluation
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
            return lengthLimitedEvaluation(content, canAdjust);
        }

        if (actionResult.actionType() == LoopActionType.MODEL_CALL
                && isUnresolvedToolCallFinish(actionResult)) {
            return unresolvedToolCallEvaluation(canAdjust);
        }

        if (actionResult.actionType() == LoopActionType.CLARIFICATION_REQUEST) {
            return clarificationEvaluation(content, canAdjust);
        }

        if (isIntermediateToolObservation(actionResult.actionType())) {
            return toolObservationEvaluation(actionResult, canAdjust);
        }

        LoopCompletionJudgment judgment = judgeCompletion(
                context,
                actionResult,
                policy,
                currentNodeCount);
        if (judgment.confidentComplete()) {
            return new LoopEvaluation(
                    LoopEvaluationDecision.COMPLETE,
                    defaultText(
                            judgment.summary(),
                            "模型 Judge 判定动作结果满足用户可见完成合同"),
                    "");
        }
        if (requiresAdjustment(judgment)) {
            return judgedIncompleteEvaluation(judgment, canAdjust);
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
     * Handles model output stopped by provider token limit.
     */
    private LoopEvaluation lengthLimitedEvaluation(
            String content,
            boolean canAdjust) {
        if (canAdjust) {
            return new LoopEvaluation(
                    LoopEvaluationDecision.ADJUST,
                    "模型输出达到 max_tokens 上限，不能作为最终结果",
                    "上一轮模型输出因 finishReason=length 被截断。"
                            + "请基于当前 Conversation Context、工具 Observation 和原始目标，"
                            + "生成一份完整但更紧凑的最终版本；不要只续写半截内容，"
                            + "也不要暴露截断或内部执行细节。上一轮内容摘要："
                            + summarize(content, 12_000));
        }
        return new LoopEvaluation(
                LoopEvaluationDecision.FAIL,
                "模型输出被截断且 LoopTree 预算已耗尽",
                "Model output was truncated by max_tokens");
    }

    /**
     * Handles provider responses that declare tool-calling but contain no parsed calls.
     */
    private LoopEvaluation unresolvedToolCallEvaluation(boolean canAdjust) {
        if (canAdjust) {
            // This is a protocol-level non-completion: the model did not return a final answer.
            return new LoopEvaluation(
                    LoopEvaluationDecision.ADJUST,
                    "Provider returned finishReason=tool_calls but no parsed tool calls",
                    "上一轮模型以 finishReason=tool_calls 结束，但框架没有收到有效 tool_call。"
                            + "请在下一轮二选一：如果确实需要工具，输出一个合法的工具调用；"
                            + "如果已有证据足够，请直接合成面向用户的最终答案。"
                            + "不要把“我将继续搜索/并行搜索/重新搜集资料”当作最终回答。");
        }
        return new LoopEvaluation(
                LoopEvaluationDecision.FAIL,
                "Provider returned tool_calls without parsed tool calls and LoopTree budget exhausted",
                "finishReason=tool_calls but toolCallCount=0");
    }

    /**
     * Handles a clarification answer observation.
     */
    private LoopEvaluation clarificationEvaluation(
            String content,
            boolean canAdjust) {
        if (canAdjust) {
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

    /**
     * Handles tool/RAG/file/skill observations.
     */
    private LoopEvaluation toolObservationEvaluation(
            LoopActionResult actionResult,
            boolean canAdjust) {
        if (canAdjust) {
            String toolId = toolId(actionResult);
            boolean toolSuccess = toolSuccess(actionResult);
            return new LoopEvaluation(
                    LoopEvaluationDecision.ADJUST,
                    toolSuccess
                            ? "工具动作已完成，需要基于 Observation 继续生成用户结果"
                            : "工具动作失败已记录，需要基于 Observation 纠偏或降级",
                    "上一轮工具动作 "
                            + actionResult.actionType().name()
                            + (toolId.isBlank()
                                    ? ""
                                    : "（toolId=" + toolId + "）")
                            + " toolSuccess="
                            + toolSuccess
                            + " 返回："
                            + summarize(actionResult.content(), 12_000)
                            + "。请结合 Conversation Context、工具 Observation 和原始目标，"
                            + "优先选择 MODEL_CALL 合成面向用户的最终结果；"
                            + (toolSuccess
                                    ? "不要重复调用同类工具，除非需要查询一个明确不同的信息缺口。"
                                    : "不要把异常原文直接展示给用户；如仍需要工具，必须换一个明确不同的来源、参数或工具。"));
        }
        return new LoopEvaluation(
                LoopEvaluationDecision.FAIL,
                "工具动作已完成，但 LoopTree 深度或节点预算已耗尽",
                "Loop execution policy exhausted after tool observation");
    }

    /**
     * Converts a model-judge rejection into Loop evaluation.
     */
    private LoopEvaluation judgedIncompleteEvaluation(
            LoopCompletionJudgment judgment,
            boolean canAdjust) {
        if (canAdjust) {
            return new LoopEvaluation(
                    LoopEvaluationDecision.ADJUST,
                    defaultText(
                            judgment.summary(),
                            "模型 Judge 判定动作结果尚未满足完成合同"),
                    judgmentFeedback(judgment));
        }
        return new LoopEvaluation(
                LoopEvaluationDecision.FAIL,
                defaultText(
                        judgment.summary(),
                        "模型 Judge 判定动作结果未完成且预算已耗尽"),
                judgmentFeedback(judgment));
    }

    /**
     * Calls the semantic completion judge and protects domain policy from external failures.
     */
    private LoopCompletionJudgment judgeCompletion(
            RunExecutionContext context,
            LoopActionResult actionResult,
            LoopExecutionPolicy policy,
            int currentNodeCount) {
        try {
            return completionJudge.judge(
                    context,
                    actionResult,
                    policy,
                    currentNodeCount);
        } catch (RuntimeException exception) {
            return LoopCompletionJudgment.unknown(
                    "Completion judge failed: "
                            + exception.getClass().getSimpleName());
        }
    }

    /**
     * Determines whether the judge explicitly rejected completion.
     */
    private boolean requiresAdjustment(LoopCompletionJudgment judgment) {
        return judgment.decision()
                == LoopCompletionJudgmentDecision.NEED_MORE_ACTION
                || judgment.decision()
                == LoopCompletionJudgmentDecision.NEED_MORE_EVIDENCE
                || judgment.decision()
                == LoopCompletionJudgmentDecision.NEED_CLARIFICATION
                || judgment.decision()
                == LoopCompletionJudgmentDecision.INVALID_RESULT;
    }

    /**
     * Produces actionable feedback for the next LoopNode when the judge rejects completion.
     */
    private String judgmentFeedback(LoopCompletionJudgment judgment) {
        if (!judgment.feedback().isBlank()) {
            return judgment.feedback();
        }
        return switch (judgment.decision()) {
            case NEED_CLARIFICATION ->
                    "上一轮结果暴露出关键输入缺失；如果确实需要用户信息，请改用 clarification.request。";
            case NEED_MORE_EVIDENCE ->
                    "上一轮结果缺少完成目标所需的证据或来源；请补充必要证据后再合成最终答案。";
            case INVALID_RESULT ->
                    "上一轮结果是内部日志、工具原始输出或异常文本；请改写为面向用户的自然结果。";
            case NEED_MORE_ACTION ->
                    "上一轮结果只是过程性说明；请继续执行必要动作，或基于已有证据直接输出最终答案。";
            default -> "上一轮结果未满足完成合同；请重新生成最终用户可见结果。";
        };
    }

    /**
     * Rule fallback that checks whether the content can be shown as a final user answer.
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
     */
    private boolean isLengthLimited(LoopActionResult actionResult) {
        Object finishReason = actionResult.attributes().get("finishReason");
        return finishReason != null
                && "length".equalsIgnoreCase(String.valueOf(finishReason));
    }

    /**
     * Detects provider-level native-tool stop without a usable tool call payload.
     */
    private boolean isUnresolvedToolCallFinish(
            LoopActionResult actionResult) {
        Object finishReason = actionResult.attributes().get("finishReason");
        return finishReason != null
                && "tool_calls".equalsIgnoreCase(String.valueOf(finishReason))
                && intAttribute(actionResult, "toolCallCount") == 0;
    }

    /**
     * Reads an integer action attribute with safe fallback.
     */
    private int intAttribute(
            LoopActionResult actionResult,
            String key) {
        Object value = actionResult.attributes().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    /**
     * Detects model text that promises future search/query work instead of completing.
     */
    private boolean promisesAnotherToolCall(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = content.replaceAll("\\s+", "");
        return normalized.matches("(?s).*(让我|我来|我会|我将|接下来|需要|准备).{0,16}"
                + "(重新|再次|继续|并行|多维度|进一步).{0,18}"
                + "(搜索|搜集|收集|查找|查询|检索).*")
                || normalized.matches("(?s).*(重新|再次|继续|并行|多维度|进一步).{0,18}"
                + "(搜索|搜集|收集|查找|查询|检索).{0,18}"
                + "(资料|信息|来源|结果).*");
    }

    /**
     * Tool-like actions are observations, not final user artifacts.
     */
    private boolean isIntermediateToolObservation(LoopActionType actionType) {
        return actionType == LoopActionType.TOOL_CALL
                || actionType == LoopActionType.RAG_QUERY
                || actionType == LoopActionType.WEB_SEARCH
                || actionType == LoopActionType.FILE_SEARCH
                || actionType == LoopActionType.SKILL_LOAD;
    }

    /**
     * Produces fallback feedback for a non-complete model result.
     */
    private String feedback(String content) {
        if (content == null || content.isBlank()) {
            return "上一次动作返回空结果；请直接给出可展示的最终结果。";
        }
        if (clarificationNeedDetector.requiresClarification(content)) {
            return "上一次动作是在请求用户补充信息；请改用 clarification.request 动作。";
        }
        if (promisesAnotherToolCall(content)) {
            return "上一次回答承诺继续搜索或查询，但当前应基于已有工具 Observation 直接合成结果。"
                    + "请说明已有信息的局限，并给出当前能可靠回答的内容，不要承诺后续工具调用。";
        }
        return "上一次动作泄露了内部执行术语；请改写成面向用户的自然回复。";
    }

    /**
     * Reads Tool ID from action attributes.
     */
    private String toolId(LoopActionResult actionResult) {
        Object value = actionResult.attributes().get("toolId");
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * @return whether tool observation succeeded; missing success defaults to true for old rows
     */
    private boolean toolSuccess(LoopActionResult actionResult) {
        Object value = actionResult.attributes().get("success");
        return value == null || Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * Produces a short single-line summary.
     */
    private String summarize(String content) {
        return summarize(content, 180);
    }

    /**
     * Produces a bounded single-line summary.
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

    /**
     * Returns fallback text when the primary text is blank.
     */
    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
