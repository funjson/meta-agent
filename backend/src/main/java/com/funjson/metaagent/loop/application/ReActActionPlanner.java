package com.funjson.metaagent.loop.application;

import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.domain.CapabilityPlanningContext;
import com.funjson.metaagent.context.domain.LoopContextSnapshot;
import com.funjson.metaagent.loop.domain.ExecutionDerivationRequest;
import com.funjson.metaagent.loop.domain.ExecutionDerivationType;
import com.funjson.metaagent.loop.domain.LoopActionType;
import com.funjson.metaagent.loop.domain.LoopPlan;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.prompt.application.PromptRegistry;
import com.funjson.metaagent.prompt.domain.PromptUseCase;
import com.funjson.metaagent.provider.application.ModelProviderRegistry;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.stereotype.Service;

/**
 * ReAct Planning 阶段的结构化动作选择器。
 *
 * <p>它先尊重已加载 Skill Manifest 派生出的 Child Loop / Child Job 请求；
 * 否则必须调用模型输出平台动作 JSON。模型输出不可信，因此 Runtime 会做结构化校验；
 * 非法输出会让当前 Loop 失败，而不是静默退回旧的硬编码工具选择。</p>
 */
@Service
public class ReActActionPlanner {

    private static final int DEFAULT_MODEL_TOKENS = 512;
    private static final int PLANNER_TOKENS = 600;

    private final ModelProviderRegistry providers;
    private final PromptRegistry prompts;
    private final ObjectMapper objectMapper;

    /**
     * 创建 ReAct Action Planner。
     *
     * @param providers 模型 Provider 注册表
     * @param prompts Prompt 注册表
     * @param objectMapper JSON Mapper
     */
    public ReActActionPlanner(
            ModelProviderRegistry providers,
            PromptRegistry prompts,
            ObjectMapper objectMapper) {
        this.providers = providers;
        this.prompts = prompts;
        this.objectMapper = objectMapper;
    }

    /**
     * 选择当前 LoopNode 的下一步动作。
     *
     * @param context LoopNode 执行上下文
     * @param capabilityContext 当前 Capability 作用域
     * @param contextSnapshot 已构建的 ReAct 上下文快照
     * @return 结构化动作计划
     */
    public LoopPlan plan(
            RunExecutionContext context,
            CapabilityPlanningContext capabilityContext,
            LoopContextSnapshot contextSnapshot) {
        ExecutionDerivationRequest derivation =
                capabilityContext.derivationRequest();
        if (derivation != null
                && derivation.type() == ExecutionDerivationType.CHILD_JOB) {
            return LoopPlan.childJob(
                    "Child Job 完成并回传满足合同的结果",
                    derivation.reason(),
                    derivation);
        }
        if (derivation != null
                && derivation.type() == ExecutionDerivationType.CHILD_LOOP) {
            return LoopPlan.childLoop(
                    "Child LoopNode 完成其局部目标",
                    derivation.reason(),
                    derivation);
        }
        LoopPlan plan = modelPlan(
                context,
                capabilityContext,
                contextSnapshot);
        return convergeAfterToolObservation(context, plan);
    }

    /**
     * 调用模型产生结构化动作计划；非法 JSON 会阻断当前 Loop。
     */
    private LoopPlan modelPlan(
            RunExecutionContext context,
            CapabilityPlanningContext capabilityContext,
            LoopContextSnapshot contextSnapshot) {
        var prompt = prompts.render(
                PromptUseCase.LOOP_ACTION_PLANNING,
                Map.of(
                        "goal", context.goal(),
                        "contextSummary", contextSnapshot.toPromptSummary(),
                        "capabilitySummary", capabilityContext.scopedContext()
                                .instructionSummary(),
                        "feedback", context.feedback().isBlank()
                                ? "无"
                                : context.feedback()));
        var response = providers.require(context.providerId())
                .generate(new ModelRequest(
                        context.taskRunId(),
                        context.loopNodeId(),
                        "Plan next ReAct action for " + context.goal(),
                        prompt,
                        PLANNER_TOKENS));
        LoopPlan plan = parseJsonPlan(response.content());
        if (plan == null) {
            throw new RuntimeStateException(
                    "INVALID_REACT_ACTION_PLAN",
                    "Model returned an invalid ReAct action plan");
        }
        return plan;
    }

    /**
     * 工具 Observation 之后的下一轮应当默认进入结果合成。
     *
     * <p>模型可能因为“天气/搜索/最新信息”等关键词再次选择同类工具，导致
     * WEB_SEARCH → Observation → WEB_SEARCH 的 ReAct 循环。这里保留第一轮
     * 工具调用，但如果反馈已经明确包含上一轮同类工具 Observation，就强制收敛
     * 到 MODEL_CALL，让执行模型基于已有 Observation 生成最终用户回复。</p>
     */
    private LoopPlan convergeAfterToolObservation(
            RunExecutionContext context,
            LoopPlan plan) {
        if (plan.actionType() == LoopActionType.MODEL_CALL
                || plan.actionType() == LoopActionType.CLARIFICATION_REQUEST
                || plan.actionType() == LoopActionType.CHILD_LOOP
                || plan.actionType() == LoopActionType.CHILD_JOB) {
            return plan;
        }
        if (!hasPreviousObservationFor(context.feedback(), plan)) {
            return plan;
        }
        // 这不是静默吞掉工具能力，而是把“已有 Observation”转交给最终回答模型。
        return LoopPlan.modelCall(
                "基于已有工具 Observation 生成面向用户的最终结果",
                "已获得上一轮 "
                        + plan.actionType().name()
                        + " Observation，进入最终合成",
                DEFAULT_MODEL_TOKENS);
    }

    /**
     * 判断当前反馈是否已经包含同类工具 Observation。
     */
    private boolean hasPreviousObservationFor(
            String feedback,
            LoopPlan plan) {
        if (feedback == null || feedback.isBlank()) {
            return false;
        }
        String normalized = feedback.toUpperCase(Locale.ROOT);
        String actionMarker = "上一轮工具动作 "
                + plan.actionType().name()
                + " 返回";
        if (normalized.contains(actionMarker.toUpperCase(Locale.ROOT))) {
            return true;
        }
        String toolId = plan.toolId() == null ? "" : plan.toolId();
        return !toolId.isBlank()
                && normalized.contains("上一轮工具动作 TOOL_CALL 返回")
                && normalized.contains(toolId.toUpperCase(Locale.ROOT));
    }

    /**
     * 解析模型返回的 JSON 动作。
     */
    private LoopPlan parseJsonPlan(String content) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(content));
            Map<String, Object> values = objectMapper.convertValue(
                    root,
                    new TypeReference<>() {
                    });
            return parsePlan(values);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 将 Map 格式动作转换成 LoopPlan，并补齐框架工具默认 ID。
     */
    private LoopPlan parsePlan(Map<String, Object> values) {
        String rawActionType = text(values, "actionType");
        if (rawActionType.isBlank()) {
            return null;
        }
        LoopActionType actionType = normalizeActionType(rawActionType);
        if (actionType == null
                || actionType == LoopActionType.CHILD_LOOP
                || actionType == LoopActionType.CHILD_JOB) {
            return null;
        }
        String summary = defaultText(
                values,
                "summary",
                "执行 ReAct 规划选择的下一步动作");
        String criterion = defaultText(
                values,
                "completionCriterion",
                "动作返回可被 Observation/Evaluation 使用的结果");
        if (actionType == LoopActionType.MODEL_CALL) {
            return LoopPlan.modelCall(
                    criterion,
                    summary,
                    positiveInt(values.get("maxTokens"),
                            DEFAULT_MODEL_TOKENS));
        }
        String toolId = defaultToolId(
                actionType,
                text(values, "toolId"));
        Map<String, Object> arguments = arguments(values.get("arguments"));
        if (toolId.isBlank()) {
            return null;
        }
        if (actionType == LoopActionType.CLARIFICATION_REQUEST
                && !arguments.containsKey("question")) {
            return null;
        }
        return LoopPlan.toolCall(
                actionType,
                criterion,
                summary,
                toolId,
                arguments);
    }

    /**
     * 兼容模型可能输出的 SKILL_SEARCH 别名。
     */
    private LoopActionType normalizeActionType(String rawActionType) {
        String normalized = rawActionType.trim().toUpperCase();
        if ("SKILL_SEARCH".equals(normalized)) {
            return LoopActionType.TOOL_CALL;
        }
        try {
            return LoopActionType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 为框架级动作补默认 Tool ID。
     */
    private String defaultToolId(
            LoopActionType actionType,
            String explicitToolId) {
        if (!explicitToolId.isBlank()) {
            return explicitToolId;
        }
        return switch (actionType) {
            case CLARIFICATION_REQUEST -> "clarification.request";
            case RAG_QUERY -> "rag.query";
            case WEB_SEARCH -> "web.search";
            case FILE_SEARCH -> "file.search";
            case SKILL_LOAD -> "skill.load";
            case TOOL_CALL -> "skill.search";
            default -> "";
        };
    }

    /**
     * 复制 arguments Map，非法结构降级为空参数。
     */
    private Map<String, Object> arguments(Object raw) {
        if (!(raw instanceof Map<?, ?> values)) {
            return Map.of();
        }
        return values.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> String.valueOf(entry.getKey()),
                        Map.Entry::getValue));
    }

    /**
     * 读取字符串字段。
     */
    private String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 读取带默认值的字符串字段。
     */
    private String defaultText(
            Map<String, Object> values,
            String key,
            String fallback) {
        String value = text(values, key);
        return value.isBlank() ? fallback : value;
    }

    /**
     * 读取正整数。
     */
    private int positiveInt(Object raw, int fallback) {
        if (raw instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        try {
            int value = Integer.parseInt(String.valueOf(raw));
            return value > 0 ? value : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    /**
     * 移除模型可能返回的 JSON Markdown 围栏。
     */
    private String stripCodeFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }
}
