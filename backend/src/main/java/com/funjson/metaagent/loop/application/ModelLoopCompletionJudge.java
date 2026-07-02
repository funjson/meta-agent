package com.funjson.metaagent.loop.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.loop.domain.LoopActionResult;
import com.funjson.metaagent.loop.domain.LoopActionType;
import com.funjson.metaagent.loop.domain.LoopCompletionJudge;
import com.funjson.metaagent.loop.domain.LoopCompletionJudgment;
import com.funjson.metaagent.loop.domain.LoopCompletionJudgmentDecision;
import com.funjson.metaagent.loop.domain.LoopExecutionPolicy;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.prompt.application.PromptRegistry;
import com.funjson.metaagent.prompt.domain.PromptUseCase;
import com.funjson.metaagent.provider.application.ModelProviderRegistry;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelThinkingMode;
import org.springframework.stereotype.Service;

/**
 * 基于模型的 Loop 完成语义验收器。
 *
 * <p>该类属于 Loop application 层：它可以调用 PromptRegistry 和 Provider，
 * 但只通过领域层的 {@link LoopCompletionJudge} 接口向 LoopEvaluator 暴露稳定合同。</p>
 */
@Service
public class ModelLoopCompletionJudge implements LoopCompletionJudge {

    /** Judge 只需要短 JSON，不应生成长文。 */
    private static final int MAX_TOKENS = 512;

    /** 防止把过长候选结果塞进评审 Prompt，影响延迟和成本。 */
    private static final int MAX_CONTENT_CHARS = 14_000;

    private final PromptRegistry promptRegistry;
    private final ModelProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 创建模型完成验收器。
     *
     * @param promptRegistry Prompt 注册表
     * @param providerRegistry Provider 注册表
     * @param objectMapper JSON 解析器
     */
    public ModelLoopCompletionJudge(
            PromptRegistry promptRegistry,
            ModelProviderRegistry providerRegistry,
            ObjectMapper objectMapper) {
        this.promptRegistry = promptRegistry;
        this.providerRegistry = providerRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 使用当前 executor/provider 对模型候选输出做语义验收。
     *
     * @param context 当前 LoopNode 上下文
     * @param actionResult 候选动作结果
     * @param policy Loop 执行预算
     * @param currentNodeCount 当前 LoopTree 节点数量
     * @return 结构化完成判定；失败时返回 UNKNOWN 交给规则兜底
     */
    @Override
    public LoopCompletionJudgment judge(
            RunExecutionContext context,
            LoopActionResult actionResult,
            LoopExecutionPolicy policy,
            int currentNodeCount) {
        if (actionResult.actionType() != LoopActionType.MODEL_CALL) {
            return LoopCompletionJudgment.unknown(
                    "Only model-call results need model completion judgment");
        }
        try {
            Map<String, String> variables = variables(
                    context,
                    actionResult,
                    policy,
                    currentNodeCount);
            var prompt = promptRegistry.render(
                    PromptUseCase.LOOP_COMPLETION_JUDGMENT,
                    variables);
            var response = providerRegistry.require(context.providerId())
                    .generate(new ModelRequest(
                            context.taskRunId(),
                            context.loopNodeId(),
                            "Judge Loop completion for "
                                    + abbreviate(context.goal(), 160),
                            prompt,
                            MAX_TOKENS,
                            List.of(),
                            ModelThinkingMode.DISABLED,
                            context.providerId()));
            return parse(response.content());
        } catch (RuntimeException exception) {
            // Judge 是增强型语义验收，不允许 Provider/JSON 问题阻断主执行链路。
            return LoopCompletionJudgment.unknown(
                    "Model completion judge unavailable: "
                            + exception.getClass().getSimpleName());
        }
    }

    /**
     * 构造 Prompt 变量，所有结构化字段都显式序列化，避免模型误读。
     */
    private Map<String, String> variables(
            RunExecutionContext context,
            LoopActionResult actionResult,
            LoopExecutionPolicy policy,
            int currentNodeCount) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("goal", safe(context.goal()));
        variables.put("feedback", blankAsNone(context.feedback()));
        variables.put("actionType", actionResult.actionType().name());
        variables.put("source", safe(actionResult.source()));
        variables.put("finishReason", attribute(actionResult, "finishReason"));
        variables.put("toolCallCount", attribute(actionResult, "toolCallCount"));
        variables.put("policy", "maxDepth=" + policy.maxDepth()
                + ", maxLoopNodes=" + policy.maxLoopNodes()
                + ", currentNodeCount=" + currentNodeCount
                + ", currentDepth=" + context.depth());
        variables.put("content", abbreviate(
                actionResult.content(),
                MAX_CONTENT_CHARS));
        variables.put("attributes", attributesJson(actionResult));
        return variables;
    }

    /**
     * 解析模型返回的严格 JSON 合同。
     */
    private LoopCompletionJudgment parse(String content) {
        try {
            JsonNode root = objectMapper.readTree(jsonPayload(content));
            LoopCompletionJudgmentDecision decision =
                    normalizeDecision(root.path("decision").asText(""));
            double confidence = root.path("confidence").asDouble(0D);
            String summary = root.path("summary").asText("");
            String feedback = root.path("feedback").asText("");
            return new LoopCompletionJudgment(
                    decision,
                    confidence,
                    summary,
                    feedback);
        } catch (Exception exception) {
            return LoopCompletionJudgment.unknown(
                    "Model completion judge returned invalid JSON");
        }
    }

    /**
     * 兼容模型可能返回的大小写、短横线或同义决策名称。
     */
    private LoopCompletionJudgmentDecision normalizeDecision(String value) {
        String normalized = safe(value)
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "COMPLETE", "DONE", "FINAL" ->
                    LoopCompletionJudgmentDecision.COMPLETE;
            case "NEED_MORE_ACTION", "CONTINUE", "NOT_COMPLETE" ->
                    LoopCompletionJudgmentDecision.NEED_MORE_ACTION;
            case "NEED_MORE_EVIDENCE", "INSUFFICIENT_EVIDENCE" ->
                    LoopCompletionJudgmentDecision.NEED_MORE_EVIDENCE;
            case "NEED_CLARIFICATION", "ASK_USER" ->
                    LoopCompletionJudgmentDecision.NEED_CLARIFICATION;
            case "INVALID_RESULT", "INVALID", "INTERNAL_RESULT" ->
                    LoopCompletionJudgmentDecision.INVALID_RESULT;
            default -> LoopCompletionJudgmentDecision.UNKNOWN;
        };
    }

    /**
     * 提取 JSON 主体，兼容 Markdown code fence 或前后附加说明。
     */
    private String jsonPayload(String value) {
        String normalized = safe(value).trim();
        if (normalized.startsWith("```")) {
            int firstLineBreak = normalized.indexOf('\n');
            int lastFence = normalized.lastIndexOf("```");
            if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
                normalized = normalized
                        .substring(firstLineBreak + 1, lastFence)
                        .trim();
            }
        }
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return normalized.substring(start, end + 1);
        }
        return normalized;
    }

    /**
     * 序列化动作属性；失败时保留 toString 作为审计兜底。
     */
    private String attributesJson(LoopActionResult actionResult) {
        try {
            return objectMapper.writeValueAsString(actionResult.attributes());
        } catch (Exception exception) {
            return String.valueOf(actionResult.attributes());
        }
    }

    /**
     * 读取动作属性，缺失时返回 none，避免 Prompt 出现 null 噪声。
     */
    private String attribute(
            LoopActionResult actionResult,
            String key) {
        Object value = actionResult.attributes().get(key);
        return value == null ? "none" : String.valueOf(value);
    }

    /**
     * 空白文本归一化。
     */
    private String blankAsNone(String value) {
        String normalized = safe(value);
        return normalized.isBlank() ? "none" : normalized;
    }

    /**
     * 安全字符串。
     */
    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 截断过长文本，保留头尾，避免只看到开头而丢失结论。
     */
    private String abbreviate(
            String value,
            int maxChars) {
        String normalized = safe(value);
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        int head = Math.max(0, maxChars * 2 / 3);
        int tail = Math.max(0, maxChars - head - 80);
        return normalized.substring(0, head)
                + "\n\n...[content truncated for completion judgment]...\n\n"
                + normalized.substring(normalized.length() - tail);
    }
}
