package com.funjson.metaagent.loop.application;

import java.util.Map;
import java.util.UUID;

import com.funjson.metaagent.capability.application.CapabilityApplicationService;
import com.funjson.metaagent.capability.domain.CapabilityPlanningContext;
import com.funjson.metaagent.context.application.LoopContextBuilder;
import com.funjson.metaagent.context.domain.LoopContextSnapshot;
import com.funjson.metaagent.loop.domain.LoopActionResult;
import com.funjson.metaagent.loop.domain.LoopActionType;
import com.funjson.metaagent.loop.domain.ClarificationNeedDetector;
import com.funjson.metaagent.loop.domain.ExecutionDerivationPolicy;
import com.funjson.metaagent.loop.domain.LoopEvaluation;
import com.funjson.metaagent.loop.domain.LoopEvaluationDecision;
import com.funjson.metaagent.loop.domain.LoopCompletionPolicy;
import com.funjson.metaagent.loop.domain.LoopCorrectionPolicy;
import com.funjson.metaagent.loop.domain.LoopExecutionException;
import com.funjson.metaagent.loop.domain.LoopExecutionPolicy;
import com.funjson.metaagent.loop.domain.LoopOutcome;
import com.funjson.metaagent.loop.domain.LoopPhaseType;
import com.funjson.metaagent.loop.domain.LoopPlan;
import com.funjson.metaagent.loop.domain.RuntimeClarificationContractBuilder;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.provider.application.ModelProviderRegistry;
import com.funjson.metaagent.provider.domain.ModelProvider;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;
import com.funjson.metaagent.provider.domain.ModelThinkingMode;
import com.funjson.metaagent.provider.domain.ModelToolCall;
import com.funjson.metaagent.provider.domain.ModelToolSpec;
import com.funjson.metaagent.prompt.application.PromptRegistry;
import com.funjson.metaagent.prompt.domain.PromptUseCase;
import com.funjson.metaagent.recovery.application.RuntimeLeaseService;
import com.funjson.metaagent.recovery.domain.ResumeExecutionSnapshot;
import com.funjson.metaagent.recovery.domain.LoopNodeResumeContext;
import com.funjson.metaagent.runtime.domain.ChildJobOutcome;
import com.funjson.metaagent.runtime.domain.ClarificationAnswerOutcome;
import com.funjson.metaagent.tool.application.ToolExecutionService;
import com.funjson.metaagent.tool.application.ToolInvocationCommand;
import com.funjson.metaagent.tool.application.ToolCatalogService;
import org.springframework.stereotype.Service;

/**
 * 编排 Loop Kernel 的模型动作、运行持久化和局部验收。
 *
 * <p>每个 LoopNode 执行完整内部阶段闭环，Evaluation 可以派生 Child LoopNode。</p>
 */
@Service
public class RuntimeExecutionService implements LoopRunExecutor {

    private final RuntimeTransactionService transactions;
    private final ModelProviderRegistry modelProviders;
    private final PromptRegistry promptRegistry;
    private final ReActActionPlanner actionPlanner;
    private final LoopCompletionPolicy loopCompletionPolicy;
    private final LoopCorrectionPolicy loopCorrectionPolicy;
    private final ClarificationNeedDetector clarificationNeedDetector;
    private final ExecutionDerivationPolicy derivationPolicy;
    private final CapabilityApplicationService capabilityApplications;
    private final RuntimeLeaseService runtimeLeases;
    private final LoopContextBuilder loopContextBuilder;
    private final ToolExecutionService toolExecutionService;
    private final ToolCatalogService toolCatalogService;
    private final RuntimeClarificationContractBuilder clarificationContracts =
            new RuntimeClarificationContractBuilder();

    /**
     * 创建 Loop 执行服务。
     *
     * @param transactions Loop 事务服务
     * @param modelProviders Provider Registry
     * @param promptRegistry Prompt Registry
     * @param actionPlanner ReAct 动作规划器
     * @param loopCompletionPolicy Loop 局部验收策略
     * @param loopCorrectionPolicy Loop 纠偏策略
     * @param clarificationNeedDetector 澄清需求检测器
     * @param derivationPolicy 派生请求边界策略
     * @param capabilityApplications Capability 应用服务
     * @param runtimeLeases TaskRun 租约服务
     * @param loopContextBuilder Loop 上下文构建器
     */
    public RuntimeExecutionService(
            RuntimeTransactionService transactions,
            ModelProviderRegistry modelProviders,
            PromptRegistry promptRegistry,
            ReActActionPlanner actionPlanner,
            LoopCompletionPolicy loopCompletionPolicy,
            LoopCorrectionPolicy loopCorrectionPolicy,
            ClarificationNeedDetector clarificationNeedDetector,
            ExecutionDerivationPolicy derivationPolicy,
            CapabilityApplicationService capabilityApplications,
            RuntimeLeaseService runtimeLeases,
            LoopContextBuilder loopContextBuilder,
            ToolExecutionService toolExecutionService,
            ToolCatalogService toolCatalogService) {
        this.transactions = transactions;
        this.modelProviders = modelProviders;
        this.promptRegistry = promptRegistry;
        this.actionPlanner = actionPlanner;
        this.loopCompletionPolicy = loopCompletionPolicy;
        this.loopCorrectionPolicy = loopCorrectionPolicy;
        this.clarificationNeedDetector = clarificationNeedDetector;
        this.derivationPolicy = derivationPolicy;
        this.capabilityApplications = capabilityApplications;
        this.runtimeLeases = runtimeLeases;
        this.loopContextBuilder = loopContextBuilder;
        this.toolExecutionService = toolExecutionService;
        this.toolCatalogService = toolCatalogService;
    }

    /**
     * 执行由根节点和可选 Child LoopNode 构成的最小完整 LoopTree。
     *
     * @param rootContext 根节点上下文
     * @return Loop Outcome
     */
    @Override
    public LoopOutcome execute(RunExecutionContext rootContext) {
        LoopExecutionPolicy policy = LoopExecutionPolicy.baseline();
        RunExecutionContext context = rootContext;
        try {
            while (true) {
                runtimeLeases.heartbeat(context.taskRunId());
                CapabilityPlanningContext capabilityContext =
                        capabilityApplications.prepare(context);
                LoopContextSnapshot contextSnapshot =
                        loopContextBuilder.build(
                                context,
                                capabilityContext);
                transactions.recordCompletedPhase(
                        context,
                        LoopPhaseType.CONTEXT_BUILD,
                        "已构造 ReAct 上下文、工具目录、局部 Skill 和执行边界",
                        java.util.Map.of(
                                "taskRunId", context.taskRunId(),
                                "parentNodeId",
                                context.parentNodeId() == null
                                        ? "ROOT"
                                        : context.parentNodeId().toString()),
                        java.util.Map.of(
                                "goal", context.goal(),
                                "provider", context.providerId(),
                                "contextBlockCount",
                                contextSnapshot.blocks().size(),
                                "contextTokenBudget",
                                contextSnapshot.tokenBudget(),
                                "capabilitySources",
                                capabilityContext.scopedContext()
                                        .sourceRefs(),
                                "maxDepth", policy.maxDepth(),
                                "maxLoopNodes", policy.maxLoopNodes()),
                        "CONTEXT_BUILT");

                LoopPlan plan = planNextAction(
                        context,
                        capabilityContext,
                        contextSnapshot);
                transactions.recordPlan(context, plan);

                if (plan.actionType() == LoopActionType.CHILD_LOOP) {
                    context = deriveChildAction(
                            context,
                            plan,
                            policy);
                    continue;
                }
                if (plan.actionType() == LoopActionType.CHILD_JOB) {
                    return suspendForChildJob(context, plan);
                }
                if (plan.actionType()
                        == LoopActionType.CLARIFICATION_REQUEST) {
                    return suspendForClarification(context, plan);
                }
                LoopActionResult actionResult =
                        executeAction(
                                context,
                                plan,
                                capabilityContext,
                                contextSnapshot);
                if (plan.actionType() == LoopActionType.MODEL_CALL
                        && clarificationNeedDetector.requiresClarification(
                        actionResult.content())) {
                    return suspendForModelClarification(
                            context,
                            clarificationPlan(
                                    context,
                                    actionResult.content()));
                }
                EvaluationStep step = evaluateAction(
                        context,
                        actionResult,
                        plan.completionCriterion(),
                        policy);
                if (step.outcome() != null) {
                    return step.outcome();
                }
                context = step.nextContext();
            }
        } catch (RuntimeException failure) {
            throw new LoopExecutionException(context, failure);
        }
    }

    /**
     * 把模型自然语言中的补充信息请求正式化为 clarification.request Tool 动作。
     *
     * @param question 模型提出的澄清问题
     * @return 澄清动作计划
     */
    private LoopPlan clarificationPlan(
            RunExecutionContext context,
            String question) {
        return LoopPlan.toolCall(
                LoopActionType.CLARIFICATION_REQUEST,
                "用户回答澄清问题后恢复当前 LoopNode",
                "模型结果表明缺少关键输入，转为正式 ClarificationRequest",
                "clarification.request",
                Map.of(
                        "question", question,
                        "contractJson",
                        clarificationContracts.build(
                                context.goal(),
                                question),
                        "blockingSummary", "模型执行阶段发现缺少会影响结果的关键输入。",
                        "reasonType", "TASK_CONTRACT_MISSING_INPUT"));
    }

    /**
     * 选择下一步动作。
     *
     * <p>支持原生 Function Calling 的 Provider 不再先走一次模型 Planner；
     * 直接把工具 Schema 暴露给执行模型，由模型在同一次调用中选择回答或 tool_call。
     * 不支持原生工具的 Provider 才回退到 JSON planner。</p>
     */
    private LoopPlan planNextAction(
            RunExecutionContext context,
            CapabilityPlanningContext capabilityContext,
            LoopContextSnapshot contextSnapshot) {
        if (capabilityContext.derivationRequest() != null) {
            return actionPlanner.plan(
                    context,
                    capabilityContext,
                    contextSnapshot);
        }
        ModelProvider provider = modelProviders.require(context.providerId());
        if (provider.supportsNativeToolCalling(context.providerId())) {
            return LoopPlan.modelCall(
                    "返回用户可见结果，或通过原生 tool_call 产生可观察工具结果",
                    "执行模型直接决定回答或调用工具",
                    1024);
        }
        return loopCorrectionPolicy.correctPlan(
                context,
                actionPlanner.plan(
                        context,
                        capabilityContext,
                        contextSnapshot));
    }

    /**
     * 按结构化计划分派模型动作。
     *
     * @param context LoopNode 上下文
     * @param plan 结构化计划
     * @param capabilityContext Skill 作用域
     * @param contextSnapshot Loop 上下文快照
     * @return 统一动作结果
     */
    private LoopActionResult executeAction(
            RunExecutionContext context,
            LoopPlan plan,
            CapabilityPlanningContext capabilityContext,
            LoopContextSnapshot contextSnapshot) {
        return switch (plan.actionType()) {
            case MODEL_CALL -> executeModelAction(
                    context,
                    plan,
                    capabilityContext,
                    contextSnapshot);
            case TOOL_CALL, RAG_QUERY, WEB_SEARCH, FILE_SEARCH, SKILL_LOAD ->
                    executeToolAction(context, plan);
            case CLARIFICATION_REQUEST -> throw new RuntimeStateException(
                    "INVALID_ACTION_DISPATCH",
                    "Clarification action must be handled by Loop scheduler");
            case CHILD_LOOP -> throw new RuntimeStateException(
                    "INVALID_ACTION_DISPATCH",
                    "Child Loop action must be handled by Loop scheduler");
            case CHILD_JOB -> throw new RuntimeStateException(
                    "INVALID_ACTION_DISPATCH",
                    "Child Job action must be handled by upper coordinator");
        };
    }

    /**
     * 执行模型动作。
     *
     * @param context LoopNode 上下文
     * @param plan 结构化计划
     * @param capabilityContext Skill 作用域
     * @param contextSnapshot Loop 上下文快照
     * @return 模型动作结果
     */
    /**
     * 执行普通 Tool/Skill 动作，并把调用边界写入 Loop Phase。
     *
     * @param context LoopNode 上下文
     * @param plan Tool 动作计划
     * @return Tool 观察结果
     */
    private LoopActionResult executeToolAction(
            RunExecutionContext context,
            LoopPlan plan) {
        transactions.recordCompletedPhase(
                context,
                LoopPhaseType.ACTION_PREPARATION,
                "已准备 Tool 调用并生成幂等键",
                Map.of(
                        "actionType", plan.actionType().name(),
                        "toolId", plan.toolId()),
                Map.of("argumentKeys", plan.toolArguments().keySet()),
                "ACTION_PREPARED");
        LoopActionResult result = toolExecutionService.invokeForLoop(
                context,
                new ToolInvocationCommand(
                        plan.toolId(),
                        plan.toolArguments(),
                        toolIdempotencyKey(context, plan),
                        context.jobId(),
                        context.taskId(),
                        context.taskRunId(),
                        context.loopRunId(),
                        context.loopNodeId()),
                plan.actionType());
        transactions.recordCompletedPhase(
                context,
                LoopPhaseType.ACTION_EXECUTION,
                "Tool 调用已完成并产生 Observation",
                Map.of(
                        "toolId", plan.toolId(),
                        "actionType", plan.actionType().name()),
                Map.of(
                        "source", result.source(),
                        "contentPresent",
                        result.content() != null
                                && !result.content().isBlank(),
                        "attributes", result.attributes()),
                "ACTION_EXECUTED");
        return result;
    }

    /**
     * 执行 clarification.request Tool 并挂起当前 LoopNode。
     *
     * @param context LoopNode 上下文
     * @param plan 澄清动作计划
     * @return 等待用户回答的 Outcome
     */
    private LoopOutcome suspendForClarification(
            RunExecutionContext context,
            LoopPlan plan) {
        LoopActionResult result = executeToolAction(context, plan);
        return suspendForClarificationResult(context, plan, result);
    }

    /**
     * 将模型输出中的追问升级为正式 ClarificationRequest。
     *
     * <p>模型动作已经占用了当前 LoopNode 的 ACTION_PREPARATION/ACTION_EXECUTION
     * phase；这里只创建 ToolInvocation 和 ClarificationRequest，不重复写动作 phase。</p>
     *
     * @param context LoopNode 上下文
     * @param plan 澄清动作计划
     * @return 等待用户回答的 Outcome
     */
    private LoopOutcome suspendForModelClarification(
            RunExecutionContext context,
            LoopPlan plan) {
        LoopActionResult result = toolExecutionService.invokeForLoop(
                context,
                new ToolInvocationCommand(
                        plan.toolId(),
                        plan.toolArguments(),
                        toolIdempotencyKey(context, plan),
                        context.jobId(),
                        context.taskId(),
                        context.taskRunId(),
                        context.loopRunId(),
                        context.loopNodeId()),
                LoopActionType.CLARIFICATION_REQUEST);
        return suspendForClarificationResult(context, plan, result);
    }

    /**
     * 根据 clarification.request Tool 结果挂起当前 LoopNode。
     *
     * @param context LoopNode 上下文
     * @param plan 澄清动作计划
     * @param result Tool 执行结果
     * @return 等待用户回答的 Outcome
     */
    private LoopOutcome suspendForClarificationResult(
            RunExecutionContext context,
            LoopPlan plan,
            LoopActionResult result) {
        Object requestId = result.attributes().get("clarificationRequestId");
        if (requestId == null) {
            throw new RuntimeStateException(
                    "CLARIFICATION_REQUEST_MISSING",
                    "clarification.request did not return a request id");
        }
        Object question = result.attributes().getOrDefault(
                "question",
                plan.toolArguments().getOrDefault("question", ""));
        return transactions.suspendForClarification(
                context,
                UUID.fromString(String.valueOf(requestId)),
                String.valueOf(question));
    }

    /** @return Tool 调用幂等键。 */
    private String toolIdempotencyKey(
            RunExecutionContext context,
            LoopPlan plan) {
        return "loop-tool:"
                + context.loopNodeId()
                + ":"
                + plan.actionType()
                + ":"
                + plan.toolId();
    }

    /**
     * 执行模型动作，并把外部调用边界拆成准备、执行和完成记录。
     *
     * @param context LoopNode 上下文
     * @param plan 模型动作计划
     * @param capabilityContext 当前 Skill/Capability 作用域
     * @param contextSnapshot 结构化上下文快照
     * @return 模型动作结果
     */
    private LoopActionResult executeModelAction(
            RunExecutionContext context,
            LoopPlan plan,
            CapabilityPlanningContext capabilityContext,
            LoopContextSnapshot contextSnapshot) {
        var prompt = renderModelPrompt(
                context,
                capabilityContext,
                contextSnapshot);
        transactions.recordCompletedPhase(
                context,
                LoopPhaseType.ACTION_PREPARATION,
                "已选择模型 Provider，并生成版本化 Prompt 与幂等调用引用",
                java.util.Map.of(
                        "actionType", plan.actionType(),
                        "provider", context.providerId()),
                java.util.Map.of(
                        "promptId", prompt.promptId(),
                        "promptVersion", prompt.version(),
                        "promptHash", prompt.contentHash()),
                "ACTION_PREPARED");

        RuntimeTransactionService.ExternalActionHandle actionHandle =
                transactions.startExternalAction(context, prompt);
        ModelResponse response;
        try {
            ModelProvider modelProvider =
                    modelProviders.require(context.providerId());
            // 外部调用严格位于数据库短事务之外。
            response = modelProvider.generate(new ModelRequest(
                    context.taskRunId(),
                    context.loopNodeId(),
                    context.goal(),
                    prompt,
                    plan.maxTokens(),
                    nativeToolsFor(context, modelProvider),
                    thinkingModeFor(context, modelProvider),
                    context.providerId()));
        } catch (RuntimeException failure) {
            transactions.failExternalAction(
                    context,
                    actionHandle,
                    failure);
            throw failure;
        }
        transactions.completeExternalAction(
                context,
                actionHandle,
                response);
        if (response.hasToolCalls()) {
            return executeModelSelectedTool(
                    context,
                    response.toolCalls().getFirst());
        }
        return LoopActionResult.fromModel(response);
    }

    /**
     * 根据 Provider 能力和纠偏策略决定本轮是否暴露原生工具。
     */
    private java.util.List<ModelToolSpec> nativeToolsFor(
            RunExecutionContext context,
            ModelProvider modelProvider) {
        if (!modelProvider.supportsNativeToolCalling(context.providerId())
                || !loopCorrectionPolicy.allowNativeTools(context)) {
            return java.util.List.of();
        }
        return toolCatalogService.modelToolSpecs();
    }

    /**
     * 计算本轮模型调用的思考模式。
     *
     * <p>v0.1 默认关闭。后续可以由 Task Contract、用户配置、预算和风险等级
     * 共同决定是否升级为 AUTO 或 ENABLED。</p>
     */
    private ModelThinkingMode thinkingModeFor(
            RunExecutionContext context,
            ModelProvider modelProvider) {
        return modelProvider.supportsThinkingMode(context.providerId())
                ? ModelThinkingMode.DISABLED
                : ModelThinkingMode.DISABLED;
    }

    /**
     * 执行模型原生 tool_call。
     *
     * <p>模型调用本身已经占用当前 LoopNode 的 ACTION_EXECUTION phase；
     * 工具调用会写入 tool_invocation 审计表并出现在 Agent Path 中，避免重复写入
     * 同一 LoopNode phase sequence。</p>
     */
    private LoopActionResult executeModelSelectedTool(
            RunExecutionContext context,
            ModelToolCall toolCall) {
        LoopActionType actionType = actionTypeForTool(toolCall.toolId());
        return toolExecutionService.invokeForLoop(
                context,
                new ToolInvocationCommand(
                        toolCall.toolId(),
                        toolCall.arguments(),
                        nativeToolIdempotencyKey(context, toolCall),
                        context.jobId(),
                        context.taskId(),
                        context.taskRunId(),
                        context.loopRunId(),
                        context.loopNodeId()),
                actionType);
    }

    /**
     * 根据 Tool ID 恢复 Loop 语义动作类型。
     */
    private LoopActionType actionTypeForTool(String toolId) {
        return switch (toolId) {
            case "web.search" -> LoopActionType.WEB_SEARCH;
            case "file.search" -> LoopActionType.FILE_SEARCH;
            case "skill.load" -> LoopActionType.SKILL_LOAD;
            case "rag.query" -> LoopActionType.RAG_QUERY;
            case "clarification.request" -> LoopActionType.CLARIFICATION_REQUEST;
            default -> LoopActionType.TOOL_CALL;
        };
    }

    /**
     * @return 模型原生工具调用幂等键
     */
    private String nativeToolIdempotencyKey(
            RunExecutionContext context,
            ModelToolCall toolCall) {
        return "loop-native-tool:"
                + context.loopNodeId()
                + ":"
                + toolCall.toolId();
    }

    /**
     * 从 ACTION_PREPARED 安全点恢复无副作用模型动作。
     *
     * @param snapshot 恢复快照
     * @return LoopOutcome
     */
    public LoopOutcome resumePreparedModelAction(
            ResumeExecutionSnapshot snapshot) {
        RunExecutionContext context = snapshot.context();
        try {
            if (snapshot.actionType() != LoopActionType.MODEL_CALL
                    || snapshot.actionPhaseId() == null) {
                throw new RuntimeStateException(
                        "UNSUPPORTED_RESUME_ACTION",
                        "Only prepared MODEL_CALL can resume in this slice");
            }
            runtimeLeases.heartbeat(context.taskRunId());
            CapabilityPlanningContext capabilityContext =
                    capabilityApplications.prepare(context);
            LoopContextSnapshot contextSnapshot =
                    loopContextBuilder.build(context, capabilityContext);
            var prompt = renderModelPrompt(
                    context,
                    capabilityContext,
                    contextSnapshot);
            RuntimeTransactionService.ExternalActionHandle handle =
                    transactions.reopenExternalAction(
                            snapshot.actionPhaseId());
            ModelResponse response;
            try {
                ModelProvider modelProvider =
                        modelProviders.require(context.providerId());
                // MODEL_CALL 的 side-effect class 为 NONE，可复用原幂等边界安全重试。
                response = modelProvider.generate(new ModelRequest(
                        context.taskRunId(),
                        context.loopNodeId(),
                        context.goal(),
                        prompt,
                        snapshot.maxTokens(),
                        nativeToolsFor(context, modelProvider),
                        thinkingModeFor(context, modelProvider),
                        context.providerId()));
            } catch (RuntimeException failure) {
                transactions.failExternalAction(
                        context,
                        handle,
                        failure);
                throw failure;
            }
            transactions.completeExternalAction(
                    context,
                    handle,
                    response);
            EvaluationStep step = evaluateAction(
                    context,
                    LoopActionResult.fromModel(response),
                    snapshot.completionCriterion(),
                    LoopExecutionPolicy.baseline());
            if (step.outcome() != null) {
                return step.outcome();
            }
            return execute(step.nextContext());
        } catch (RuntimeException failure) {
            throw new LoopExecutionException(context, failure);
        }
    }

    /**
     * Child Job 完成后继续 origin LoopNode 的 Observation/Evaluation。
     *
     * @param origin origin LoopNode 恢复上下文
     * @param childJobOutcome Child Job 结果
     * @return origin LoopRun Outcome
     */
    public LoopOutcome completeRecoveredChildJobAction(
            LoopNodeResumeContext origin,
            ChildJobOutcome childJobOutcome) {
        RunExecutionContext context = origin.context();
        try {
            transactions.resumeAfterChildExecution(context);
            transactions.recordCompletedPhase(
                    context,
                    LoopPhaseType.ACTION_EXECUTION,
                    "阻塞型 Child Job 已完成，origin LoopNode 继续执行",
                    java.util.Map.of(
                            "childJobId",
                            childJobOutcome.childJobId()),
                    java.util.Map.of(
                            "status",
                            childJobOutcome.status(),
                            "evidenceCount",
                            childJobOutcome.evidenceCount()),
                    "ACTION_EXECUTED");
            EvaluationStep step = evaluateAction(
                    context,
                    LoopActionResult.fromChildJob(childJobOutcome),
                    origin.completionCriterion(),
                    LoopExecutionPolicy.baseline());
            if (step.outcome() != null) {
                return step.outcome();
            }
            return execute(step.nextContext());
        } catch (RuntimeException failure) {
            throw new LoopExecutionException(context, failure);
        }
    }

    /**
     * 渲染模型动作 Prompt。
     *
     * @param context LoopNode 上下文
     * @param capabilityContext 局部 Skill 上下文
     * @param contextSnapshot Loop 上下文快照
     * @return 已渲染 Prompt
     */
    /**
     * 用户回答澄清后，继续 origin LoopNode 的 Observation/Evaluation。
     *
     * @param origin origin LoopNode 恢复上下文
     * @param clarificationOutcome 澄清回答结果
     * @return origin LoopRun Outcome
     */
    public LoopOutcome completeRecoveredClarificationAction(
            LoopNodeResumeContext origin,
            ClarificationAnswerOutcome clarificationOutcome) {
        RunExecutionContext context = origin.context();
        try {
            // 原 LoopNode 的 ACTION_EXECUTION 已在发起 clarification.request 前完成；
            // 人类回答是该动作的后续 Observation，不能再次写 ACTION_EXECUTION。
            EvaluationStep step = evaluateAction(
                    context,
                    LoopActionResult.fromClarification(
                            clarificationOutcome),
                    origin.completionCriterion(),
                    LoopExecutionPolicy.baseline());
            if (step.outcome() != null) {
                return step.outcome();
            }
            return execute(step.nextContext());
        } catch (RuntimeException failure) {
            throw new LoopExecutionException(context, failure);
        }
    }

    /**
     * 渲染 Loop 执行 Prompt。
     *
     * @param context LoopNode 上下文
     * @param capabilityContext 当前 Capability 作用域
     * @param contextSnapshot 结构化上下文快照
     * @return 已渲染 Prompt
     */
    private com.funjson.metaagent.prompt.domain.RenderedPrompt renderModelPrompt(
            RunExecutionContext context,
            CapabilityPlanningContext capabilityContext,
            LoopContextSnapshot contextSnapshot) {
        return promptRegistry.render(
                PromptUseCase.LOOP_EXECUTION,
                java.util.Map.of(
                        "goal", context.goal(),
                        "contextSummary",
                        """
                        TaskRun %s，LoopNode 深度 %d。
                        局部 Skill 规范：%s

                        %s
                        """
                                .formatted(
                                        context.taskRunId(),
                                        context.depth(),
                                        capabilityContext.scopedContext()
                                                .instructionSummary(),
                                        contextSnapshot.toPromptSummary())
                                .trim(),
                        "iterationNo",
                        String.valueOf(context.iterationNo()),
                        "feedback",
                        context.feedback().isBlank()
                                ? "无"
                                : context.feedback()));
    }

    /**
     * 记录 Observation/Evaluation，并决定完成或派生 Child Loop。
     *
     * @param context LoopNode 上下文
     * @param actionResult 动作结果
     * @param completionCriterion 完成判据
     * @param policy LoopTree 边界
     * @return 当前 Outcome 或下一个 Child 上下文
     */
    private EvaluationStep evaluateAction(
            RunExecutionContext context,
            LoopActionResult actionResult,
            String completionCriterion,
            LoopExecutionPolicy policy) {
        transactions.recordCompletedPhase(
                context,
                LoopPhaseType.OBSERVATION,
                "已记录动作事实和结构化结果摘要",
                java.util.Map.of(
                        "actionType", actionResult.actionType().name(),
                        "source", actionResult.source()),
                java.util.Map.of(
                        "contentPresent",
                        actionResult.content() != null
                                && !actionResult.content().isBlank(),
                        "attributes", actionResult.attributes()),
                "OBSERVATION_RECORDED");

        int nodeCount = transactions.nodeCount(context.loopRunId());
        LoopEvaluation evaluation = loopCompletionPolicy.evaluate(
                context,
                actionResult,
                policy,
                nodeCount);
        transactions.recordCompletedPhase(
                context,
                LoopPhaseType.EVALUATION,
                evaluation.summary(),
                java.util.Map.of(
                        "completionCriterion", completionCriterion),
                java.util.Map.of(
                        "decision", evaluation.decision().name(),
                        "feedback", evaluation.feedback()),
                "EVALUATION_RECORDED");

        if (evaluation.decision() == LoopEvaluationDecision.COMPLETE) {
            return new EvaluationStep(
                    transactions.complete(
                            context,
                            actionResult,
                            evaluation),
                    null);
        }
        if (evaluation.decision() == LoopEvaluationDecision.FAIL) {
            throw new RuntimeStateException(
                    "LOOP_POLICY_EXHAUSTED",
                    evaluation.summary());
        }
        transactions.recordCompletedPhase(
                context,
                LoopPhaseType.ADJUSTMENT,
                "已根据 Evaluation 形成 Child LoopNode 调整方案",
                java.util.Map.of(
                        "evaluation", evaluation.summary()),
                java.util.Map.of(
                        "feedback", evaluation.feedback(),
                        "nextDepth", context.depth() + 1),
                "ADJUSTMENT_CREATED");
        return new EvaluationStep(
                null,
                transactions.spawnChild(
                        context,
                        evaluation,
                        policy));
    }

    /**
     * 按步骤型 Skill 派生 Child LoopNode。
     *
     * @param context 父 LoopNode
     * @param plan Child Loop 计划
     * @param policy LoopTree 边界
     * @return Child LoopNode 上下文
     */
    private RunExecutionContext deriveChildAction(
            RunExecutionContext context,
            LoopPlan plan,
            LoopExecutionPolicy policy) {
        transactions.recordCompletedPhase(
                context,
                LoopPhaseType.ACTION_PREPARATION,
                "已校验步骤型 Skill 的 Child Loop 派生合同",
                java.util.Map.of(
                        "actionType", LoopActionType.CHILD_LOOP.name(),
                        "idempotencyKey",
                        plan.derivationRequest().idempotencyKey()),
                java.util.Map.of(
                        "childGoal",
                        plan.derivationRequest().childGoal()),
                "ACTION_PREPARED");
        transactions.recordCompletedPhase(
                context,
                LoopPhaseType.ADJUSTMENT,
                plan.summary(),
                java.util.Map.of(
                        "derivationType",
                        plan.derivationRequest().type().name()),
                java.util.Map.of(
                        "childGoal",
                        plan.derivationRequest().childGoal(),
                        "feedback",
                        plan.derivationRequest().feedback()),
                "ADJUSTMENT_CREATED");
        return transactions.spawnChild(
                context,
                plan.derivationRequest(),
                policy);
    }

    /**
     * 校验并挂起阻塞型 Child Job 派生动作。
     *
     * @param context origin LoopNode 上下文
     * @param plan 结构化计划
     * @return 等待上层物化 Child Job 的 Outcome
     */
    private LoopOutcome suspendForChildJob(
            RunExecutionContext context,
            LoopPlan plan) {
        if (plan.derivationRequest() == null
                || plan.derivationRequest().type()
                != com.funjson.metaagent.loop.domain.ExecutionDerivationType
                        .CHILD_JOB) {
            throw new RuntimeStateException(
                    "INVALID_CHILD_JOB_ACTION",
                    "Child Job action requires a child-job derivation request");
        }
        var request = plan.derivationRequest().childJobRequest();
        derivationPolicy.requireChildJobRequest(context, request);
        transactions.recordCompletedPhase(
                context,
                LoopPhaseType.ACTION_PREPARATION,
                "已校验 Child Job 派生请求与中立合同",
                java.util.Map.of(
                        "actionType", LoopActionType.CHILD_JOB.name(),
                        "idempotencyKey",
                        plan.derivationRequest().idempotencyKey()),
                java.util.Map.of(
                        "goal", request.goal(),
                        "templateRef",
                        request.templateRef() == null
                                ? "DYNAMIC"
                                : request.templateRef()),
                "ACTION_PREPARED");
        return transactions.suspendForChildJob(context, request);
    }

    /**
     * 一次动作评估后的执行分支。
     *
     * @param outcome 已完成 Outcome
     * @param nextContext 待执行 Child LoopNode
     */
    private record EvaluationStep(
            LoopOutcome outcome,
            RunExecutionContext nextContext) {
    }

}
