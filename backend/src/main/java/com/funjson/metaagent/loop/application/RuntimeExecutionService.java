package com.funjson.metaagent.loop.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.funjson.metaagent.loop.domain.ExecutionDerivationRequest;
import com.funjson.metaagent.loop.domain.LoopEvaluation;
import com.funjson.metaagent.loop.domain.LoopEvaluationDecision;
import com.funjson.metaagent.loop.domain.LoopCompletionPolicy;
import com.funjson.metaagent.loop.domain.LoopCorrectionPolicy;
import com.funjson.metaagent.loop.domain.LoopExecutionException;
import com.funjson.metaagent.loop.domain.LoopExecutionPolicy;
import com.funjson.metaagent.loop.domain.LoopOutcome;
import com.funjson.metaagent.loop.domain.LoopPhaseType;
import com.funjson.metaagent.loop.domain.LoopPlan;
import com.funjson.metaagent.loop.domain.LoopToolExposurePolicy;
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
import com.funjson.metaagent.runtime.application.port.out.TaskIntentScopeStore;
import com.funjson.metaagent.runtime.domain.TaskIntentScope;
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

    private static final int DEFAULT_MODEL_CALL_TOKENS = 1024;
    private static final int LONG_FORM_MODEL_CALL_TOKENS = 2048;
    private static final int RESEARCH_REPORT_MODEL_CALL_TOKENS = 4096;

    private final RuntimeTransactionService transactions;
    private final ModelProviderRegistry modelProviders;
    private final PromptRegistry promptRegistry;
    private final ReActActionPlanner actionPlanner;
    private final LoopCompletionPolicy loopCompletionPolicy;
    private final LoopCorrectionPolicy loopCorrectionPolicy;
    private final LoopToolExposurePolicy toolExposurePolicy =
            new LoopToolExposurePolicy();
    private final ClarificationNeedDetector clarificationNeedDetector;
    private final ExecutionDerivationPolicy derivationPolicy;
    private final CapabilityApplicationService capabilityApplications;
    private final RuntimeLeaseService runtimeLeases;
    private final LoopContextBuilder loopContextBuilder;
    private final ToolExecutionService toolExecutionService;
    private final ToolCatalogService toolCatalogService;
    private final TaskIntentScopeStore taskIntentScopes;
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
     * @param toolExecutionService framework tool execution service
     * @param toolCatalogService framework tool catalog
     * @param taskIntentScopes task-scoped intent snapshot reader
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
            ToolCatalogService toolCatalogService,
            TaskIntentScopeStore taskIntentScopes) {
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
        this.taskIntentScopes = taskIntentScopes;
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
                                contextSnapshot,
                                policy);
                if (actionResult.actionType() == LoopActionType.MODEL_CALL
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
                    modelCallTokenBudget(context));
        }
        LoopPlan planned = actionPlanner.plan(
                context,
                capabilityContext,
                contextSnapshot);
        TaskIntentScope intentScope = taskIntentScopes.findByJobId(
                        context.jobId())
                .orElse(TaskIntentScope.unspecified());
        return loopCorrectionPolicy.correctPlan(
                context,
                enforceToolExposure(context, intentScope, planned));
    }

    /**
     * Applies the same task-scoped tool visibility to fallback JSON planner
     * output that native function calling already receives through schemas.
     *
     * @param context LoopNode context
     * @param plan planner output
     * @return original plan or a safe model-call convergence plan
     */
    private LoopPlan enforceToolExposure(
            RunExecutionContext context,
            TaskIntentScope intentScope,
            LoopPlan plan) {
        if (!isToolPlan(plan)
                || toolExposurePolicy.allowNativeTool(
                        context,
                        intentScope,
                        plan.toolId())) {
            return plan;
        }
        // The fallback planner saw a broad tool catalog, but the current Task
        // goal does not authorize that tool. Converge back to model synthesis
        // instead of executing a sibling task's capability by accident.
        return LoopPlan.modelCall(
                "在当前任务目标范围内生成用户可见结果",
                "工具可见性收束：计划中的工具不属于当前 Task/Loop 目标",
                modelCallTokenBudget(context));
    }

    /**
     * Checks whether a LoopPlan performs a framework tool action.
     */
    private boolean isToolPlan(LoopPlan plan) {
        return switch (plan.actionType()) {
            case TOOL_CALL, RAG_QUERY, WEB_SEARCH, FILE_SEARCH, SKILL_LOAD ->
                    true;
            default -> false;
        };
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
    /**
     * Chooses a model output budget based on task shape and recovery feedback.
     *
     * @param context current LoopNode execution context
     * @return max completion tokens for the next model action
     */
    private int modelCallTokenBudget(RunExecutionContext context) {
        String signal = (context.goal() + "\n" + context.feedback())
                .toLowerCase();
        if (signal.matches("(?s).*(deep[- ]?research|research report|"
                + "evidence matrix|report synthesis|quality review|"
                + "深度研究|研究报告|证据矩阵|结构化报告|调研报告).*")) {
            return RESEARCH_REPORT_MODEL_CALL_TOKENS;
        }
        if (signal.matches("(?s).*(报告|方案|总结|分析|对比|长文|完整版本|"
                + "不要截断|被截断|finishreason=length).*")) {
            return LONG_FORM_MODEL_CALL_TOKENS;
        }
        return DEFAULT_MODEL_CALL_TOKENS;
    }

    /**
     * Dispatches the structured plan into a model, tool, or scheduler action.
     *
     * @param context LoopNode context
     * @param plan structured action plan
     * @param capabilityContext scoped Skill context
     * @param contextSnapshot assembled Loop context snapshot
     * @return unified action result
     */
    private LoopActionResult executeAction(
            RunExecutionContext context,
            LoopPlan plan,
            CapabilityPlanningContext capabilityContext,
            LoopContextSnapshot contextSnapshot,
            LoopExecutionPolicy policy) {
        return switch (plan.actionType()) {
            case MODEL_CALL -> executeModelAction(
                    context,
                    plan,
                    capabilityContext,
                    contextSnapshot,
                    policy);
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
            LoopContextSnapshot contextSnapshot,
            LoopExecutionPolicy policy) {
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
            return executeModelSelectedTools(
                    context,
                    response.toolCalls(),
                    policy);
        }
        return LoopActionResult.fromModel(response);
    }

    /**
     * 根据 Provider 能力和纠偏策略决定本轮是否暴露原生工具。
     */
    private java.util.List<ModelToolSpec> nativeToolsFor(
            RunExecutionContext context,
            ModelProvider modelProvider) {
        if (!modelProvider.supportsNativeToolCalling(context.providerId())) {
            return java.util.List.of();
        }
        TaskIntentScope intentScope = taskIntentScopes.findByJobId(
                        context.jobId())
                .orElse(TaskIntentScope.unspecified());
        return toolCatalogService.modelToolSpecs().stream()
                // clarification.request 是 Loop/Control 的状态迁移协议，
                // 不作为普通原生工具暴露给模型，避免绕过 WAITING_HUMAN 流程。
                .filter(tool -> !"clarification.request".equals(tool.toolId()))
                // Mixed-turn Jobs share Conversation facts, but native tools
                // must stay scoped to the current Task goal. This prevents a
                // personal-introduction Loop from calling weather/web tools
                // only because a sibling weather Job exists in the same turn.
                .filter(tool -> toolExposurePolicy.allowNativeTool(
                        context,
                        intentScope,
                        tool.toolId()))
                .filter(tool -> loopCorrectionPolicy.allowNativeTool(
                        context,
                        tool.toolId()))
                .toList();
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
     * <p>父 LoopNode 只表达“模型已经决定调用哪些工具”；每个实际工具调用都会
     * 物化为一个 Child LoopNode。这样 Agent Path 可以看到 ReAct 的递归结构：
     * 父节点负责模型决策，子节点负责单个工具动作。</p>
     */
    private LoopActionResult executeModelSelectedTools(
            RunExecutionContext context,
            List<ModelToolCall> toolCalls,
            LoopExecutionPolicy policy) {
        List<LoopActionResult> results = new ArrayList<>();
        for (int index = 0; index < toolCalls.size(); index++) {
            // 父节点已经记录模型决策；每个真实工具动作单独落到子 LoopNode。
            results.add(executeModelSelectedToolChild(
                    context,
                    toolCalls.get(index),
                    index,
                    policy));
        }
        transactions.resumeAfterChildExecution(context);
        return aggregateModelSelectedToolResults(context, toolCalls, results);
    }

    /**
     * 把单个模型原生 tool_call 物化为 Child LoopNode 并执行。
     *
     * @param parent 父 LoopNode，上面已经完成模型决策
     * @param toolCall 模型选择的工具调用
     * @param index 同一模型响应中的序号
     * @param policy LoopTree 执行边界
     * @return 工具 Observation，供父 LoopNode 继续感知与评估
     */
    private LoopActionResult executeModelSelectedToolChild(
            RunExecutionContext parent,
            ModelToolCall toolCall,
            int index,
            LoopExecutionPolicy policy) {
        LoopActionType actionType = actionTypeForTool(toolCall.toolId());
        RunExecutionContext child = transactions.spawnChild(
                parent,
                ExecutionDerivationRequest.childLoop(
                        nativeToolChildIdempotencyKey(parent, toolCall, index),
                        "model-native-tool-call",
                        "执行工具调用 " + toolCall.toolId(),
                        "父 LoopNode 已完成模型决策；本节点只执行第 "
                                + (index + 1)
                                + " 个原生工具调用。"),
                policy);
        transactions.recordCompletedPhase(
                child,
                LoopPhaseType.CONTEXT_BUILD,
                "已继承父 LoopNode 的原生工具调用决策",
                Map.of(
                        "parentLoopNodeId", parent.loopNodeId(),
                        "toolCallIndex", index),
                Map.of(
                        "toolId", toolCall.toolId(),
                        "functionName", toolCall.functionName(),
                        "argumentKeys", toolCall.arguments().keySet()),
                "CONTEXT_BUILT");
        LoopPlan childPlan = LoopPlan.toolCall(
                actionType,
                "单个原生工具调用完成后把 Observation 回传给父 LoopNode",
                "执行模型选择的工具：" + toolCall.toolId(),
                toolCall.toolId(),
                toolCall.arguments());
        transactions.recordPlan(child, childPlan);
        LoopActionResult result = executeToolAction(child, childPlan);
        transactions.completeChildLoopNode(child, result);
        return result;
    }

    /**
     * 聚合同一模型响应中的多个工具 Observation。
     *
     * <p>批量 Observation 仍然只对应当前 LoopNode 的一次动作结果，避免重复写
     * phase；具体工具调用通过 tool_invocation 表和 Agent Path 展开。</p>
     *
     * @param context LoopNode 上下文
     * @param toolCalls 模型返回的原生工具调用
     * @param results 工具执行结果
     * @return 聚合后的动作结果
     */
    private LoopActionResult aggregateModelSelectedToolResults(
            RunExecutionContext context,
            List<ModelToolCall> toolCalls,
            List<LoopActionResult> results) {
        LoopActionType actionType = aggregateActionType(results);
        List<Map<String, Object>> resultAttributes = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        boolean allSucceeded = true;
        for (int index = 0; index < results.size(); index++) {
            LoopActionResult result = results.get(index);
            ModelToolCall toolCall = toolCalls.get(index);
            boolean success = toolSuccess(result);
            allSucceeded = allSucceeded && success;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", index);
            item.put("toolId", toolCall.toolId());
            item.put("functionName", toolCall.functionName());
            item.put("success", success);
            item.put("source", result.source());
            item.put("attributes", result.attributes());
            resultAttributes.add(item);
            content.append("工具调用 #")
                    .append(index + 1)
                    .append(" toolId=")
                    .append(toolCall.toolId())
                    .append(" success=")
                    .append(success)
                    .append(System.lineSeparator())
                    .append(result.content() == null
                            ? ""
                            : result.content())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("toolBatch", true);
        attributes.put("toolCallCount", results.size());
        attributes.put("success", allSucceeded);
        attributes.put("toolId", aggregateToolId(toolCalls));
        attributes.put("results", resultAttributes);
        return new LoopActionResult(
                actionType,
                "tool-batch:" + context.loopNodeId(),
                content.toString().trim(),
                attributes);
    }

    /**
     * @return 批量工具结果的语义动作类型。
     */
    private LoopActionType aggregateActionType(
            List<LoopActionResult> results) {
        LoopActionType first = results.getFirst().actionType();
        boolean same = results.stream()
                .allMatch(result -> result.actionType() == first);
        return same ? first : LoopActionType.TOOL_CALL;
    }

    /**
     * @return 批量工具结果的代表性 Tool ID。
     */
    private String aggregateToolId(List<ModelToolCall> toolCalls) {
        String first = toolCalls.getFirst().toolId();
        boolean same = toolCalls.stream()
                .allMatch(toolCall -> first.equals(toolCall.toolId()));
        return same ? first : "tool.batch";
    }

    /**
     * @return 工具结果是否成功；旧结果缺少 success 字段时按成功处理。
     */
    private boolean toolSuccess(LoopActionResult result) {
        Object success = result.attributes().get("success");
        return success == null || Boolean.parseBoolean(String.valueOf(success));
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
     * @return 模型原生工具调用对应 Child LoopNode 的幂等键。
     */
    private String nativeToolChildIdempotencyKey(
            RunExecutionContext context,
            ModelToolCall toolCall,
            int index) {
        return "loop-native-tool-child:"
                + context.loopNodeId()
                + ":"
                + nativeToolCallIdentity(toolCall, index);
    }

    /**
     * @return Provider tool_call ID 不稳定或为空时的稳定本地标识。
     */
    private String nativeToolCallIdentity(
            ModelToolCall toolCall,
            int index) {
        String providerToolCallId = toolCall.id() == null
                || toolCall.id().isBlank()
                        ? "index-" + index
                        : toolCall.id();
        return index
                + ":"
                + toolCall.toolId()
                + ":"
                + providerToolCallId;
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
            LoopActionResult actionResult = response.hasToolCalls()
                    ? executeModelSelectedTools(
                            context,
                            response.toolCalls(),
                            LoopExecutionPolicy.baseline())
                    : LoopActionResult.fromModel(response);
            EvaluationStep step = evaluateAction(
                    context,
                    actionResult,
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
