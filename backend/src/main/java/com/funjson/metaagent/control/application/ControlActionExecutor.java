package com.funjson.metaagent.control.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.clarification.application.ClarificationService;
import com.funjson.metaagent.clarification.application.ClarificationUserResponseRenderer;
import com.funjson.metaagent.clarification.domain.ClarificationRequest;
import com.funjson.metaagent.clarification.domain.ClarificationResolution;
import com.funjson.metaagent.control.api.ControlDecisionView;
import com.funjson.metaagent.control.application.port.out.ControlTurnStore;
import com.funjson.metaagent.conversation.application.port.out.ConversationStore;
import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.context.domain.ContextEnvelope;
import com.funjson.metaagent.context.application.ConversationFactService;
import com.funjson.metaagent.context.domain.ContextFact;
import com.funjson.metaagent.intent.application.PendingInteractionCompletionPolicy;
import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.intent.domain.IntentRiskLevel;
import com.funjson.metaagent.intent.domain.IntentType;
import com.funjson.metaagent.intent.domain.PendingInteractionCompletion;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import com.funjson.metaagent.intent.domain.TurnAction;
import com.funjson.metaagent.intent.domain.TurnActionType;
import com.funjson.metaagent.intent.domain.TurnRoutingPlan;
import com.funjson.metaagent.job.api.JobView;
import com.funjson.metaagent.job.application.JobService;
import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.loop.application.RuntimeTransactionService;
import com.funjson.metaagent.recovery.application.port.out.RecoveryStore;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.task.domain.TaskStatus;
import org.springframework.stereotype.Service;

/**
 * Executes ordered Control actions produced by TurnRouter.
 *
 * <p>The executor is intentionally thin: it delegates actual Job creation,
 * clarification recovery and runtime resume to the existing services. Its job
 * is to keep ControlTurnInitializer from becoming a large routing switch.</p>
 */
@Service
public class ControlActionExecutor {

    private final ConversationStore conversationStore;
    private final ControlTurnStore controlTurnStore;
    private final ControlTurnGraphCompiler graphCompiler;
    private final ControlJobInitializationService jobInitializationService;
    private final JobService jobService;
    private final ClarificationService clarificationService;
    private final PendingInteractionCompletionPolicy pendingCompletionPolicy;
    private final ConversationFactService conversationFactService;
    private final RecoveryStore recoveryStore;
    private final RuntimeTransactionService runtimeTransactions;
    private final ClarificationUserResponseRenderer clarificationResponseRenderer;
    private final ObjectMapper objectMapper;

    /**
     * Creates a Control action executor.
     *
     * @param conversationStore Conversation persistence port
     * @param controlTurnStore ControlTurn persistence port
     * @param graphCompiler turn graph compiler
     * @param jobInitializationService root Job initialization service
     * @param jobService Job service
     * @param clarificationService Clarification service
     * @param pendingCompletionPolicy pending interaction completion policy
     * @param conversationFactService conversation fact service
     * @param recoveryStore recovery read model
     * @param runtimeTransactions runtime transaction service
     * @param clarificationResponseRenderer clarification user renderer
     * @param objectMapper JSON mapper
     */
    public ControlActionExecutor(
            ConversationStore conversationStore,
            ControlTurnStore controlTurnStore,
            ControlTurnGraphCompiler graphCompiler,
            ControlJobInitializationService jobInitializationService,
            JobService jobService,
            ClarificationService clarificationService,
            PendingInteractionCompletionPolicy pendingCompletionPolicy,
            ConversationFactService conversationFactService,
            RecoveryStore recoveryStore,
            RuntimeTransactionService runtimeTransactions,
            ClarificationUserResponseRenderer clarificationResponseRenderer,
            ObjectMapper objectMapper) {
        this.conversationStore = conversationStore;
        this.controlTurnStore = controlTurnStore;
        this.graphCompiler = graphCompiler;
        this.jobInitializationService = jobInitializationService;
        this.jobService = jobService;
        this.clarificationService = clarificationService;
        this.pendingCompletionPolicy = pendingCompletionPolicy;
        this.conversationFactService = conversationFactService;
        this.recoveryStore = recoveryStore;
        this.runtimeTransactions = runtimeTransactions;
        this.clarificationResponseRenderer = clarificationResponseRenderer;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes a routing plan and persists a single ControlDecision.
     *
     * @param context Control turn execution context
     * @param plan ordered routing plan
     * @return initialization result consumed by ControlKernel
     */
    public ControlTurnInitialization execute(
            ControlTurnExecutionContext context,
            TurnRoutingPlan plan) {
        ExecutionState state = new ExecutionState();
        ControlExecutionPlan executionPlan = graphCompiler.compile(plan);
        for (ControlExecutionNode node : executionPlan.nodes()) {
            if (state.stopAllActions) {
                break;
            }
            if (executionPlan.blockedByUpstream(
                    node.nodeId(),
                    state.blockedNodeIds)) {
                continue;
            }
            executeAction(context, plan, node, state);
        }
        if (state.immediateAssistantJob != null) {
            // Keep the immediate clarification message attached to the Job
            // that is still waiting, even if later mixed actions created and
            // dispatched another independent Job.
            state.representativeJob = state.immediateAssistantJob;
            conversationStore.linkMessageToJob(
                    context.userMessageId(),
                    state.immediateAssistantJob.id());
            controlTurnStore.attachJob(
                    context.controlTurnId(),
                    state.immediateAssistantJob.id());
        }
        IntentRecognition recognition = decisionRecognition(plan, state);
        controlTurnStore.insertDecision(
                UUID.randomUUID(),
                context.controlTurnId(),
                context.conversation().id(),
                context.userMessageId(),
                state.representativeJob == null
                        ? null
                        : state.representativeJob.id(),
                recognition,
                json(recognition.constraints()),
                state.taskGraph == null ? null : json(state.taskGraph));
        controlTurnStore.completeTurn(context.controlTurnId());
        ControlDecisionView decision = controlTurnStore.findDecision(
                        context.controlTurnId())
                .orElseThrow();
        return new ControlTurnInitialization(
                context.controlTurnId(),
                context.conversation(),
                conversationStore.findMessageByIdempotencyKey(
                                context.idempotencyKey())
                        .orElseThrow(),
                decision,
                state.representativeJob,
                state.resumeTaskRunId,
                state.immediateAssistantMessage,
                state.immediateAssistantMessageType,
                state.dispatches);
    }

    /**
     * Executes one Control action.
     */
    private void executeAction(
            ControlTurnExecutionContext context,
            TurnRoutingPlan plan,
            ControlExecutionNode node,
            ExecutionState state) {
        TurnAction action = node.action();
        switch (action.actionType()) {
            case EXPLAIN_PENDING_REQUIREMENTS ->
                    explainPendingRequirements(
                            context,
                            node.nodeId(),
                            action,
                            state);
            case ASK_DISAMBIGUATION ->
                    requirePendingInteractionDisambiguation(
                            context,
                            node.nodeId(),
                            action,
                            state);
            case CLARIFICATION_NO_TARGET ->
                    clarificationAnswerWithoutTarget(
                            node.nodeId(),
                            action,
                            state);
            case ANSWER_PENDING ->
                    answerClarification(
                            context,
                            node.nodeId(),
                            action,
                            state);
            case CREATE_JOB ->
                    createJob(
                            context,
                            node,
                            state);
            case CONTROL_MESSAGE -> {
                state.primaryRecognition = action.recognition();
            }
            default -> throw new IllegalStateException(
                    "Unsupported turn action: " + action.actionType());
        }
    }

    /**
     * Creates and registers a new root Job.
     */
    private void createJob(
            ControlTurnExecutionContext context,
            ControlExecutionNode node,
            ExecutionState state) {
        TurnAction action = node.action();
        IntentRecognition recognition = action.recognition();
        if (recognition == null) {
            throw new IllegalArgumentException("CREATE_JOB requires recognition");
        }
        var initializedJob = jobInitializationService.initializeRootJob(
                context.conversation(),
                context.userMessageId(),
                context.request(),
                new JobInitializationSpec(
                        node.nodeId(),
                        action.sourceSpan(),
                        action.originalText(),
                        action.canonicalGoal(),
                        node.taskType(),
                        recognition),
                context.modelClassificationAllowed());
        JobView job = initializedJob.job();
        state.representativeJob = job;
        state.taskGraph = initializedJob.taskGraph();
        state.primaryRecognition = recognition;
        conversationStore.linkMessageToJob(context.userMessageId(), job.id());
        conversationStore.activateJobAndRetitle(
                context.conversation().id(),
                job.id(),
                recognition.goalSummary());
        controlTurnStore.attachJob(context.controlTurnId(), job.id());
        if (hasReadyTask(job)) {
            state.dispatches.add(ControlDispatchCommand.start(job));
            return;
        }
        clarificationService.findOpenByJob(job.id()).ifPresent(clarification -> {
            appendImmediateAssistantMessage(
                    state,
                    job,
                    clarification.question(),
                    "CLARIFICATION_QUESTION");
            state.blockedNodeIds.add(node.nodeId());
        });
    }

    /**
     * Handles a request to explain the currently pending clarification contract.
     */
    private void explainPendingRequirements(
            ControlTurnExecutionContext context,
            String nodeId,
            TurnAction action,
            ExecutionState state) {
        ClarificationRequest clarification = openClarification(
                context.envelope(),
                action.targetId());
        JobView job = jobService.get(clarification.jobId());
        state.representativeJob = job;
        conversationStore.linkMessageToJob(context.userMessageId(), job.id());
        controlTurnStore.attachJob(context.controlTurnId(), job.id());
        state.primaryRecognition = new IntentRecognition(
                IntentType.PENDING_INTERACTION_HELP,
                0.93,
                "TURN_ROUTER",
                job.goalSummary(),
                clarificationResponseRenderer.help(clarification),
                List.of("pending interaction help"),
                false,
                false,
                IntentRiskLevel.LOW,
                List.of("pending-interaction-help"));
        appendImmediateAssistantMessage(
                state,
                job,
                state.primaryRecognition.decisionSummary(),
                "CLARIFICATION_QUESTION");
        state.blockedNodeIds.add(nodeId);
    }

    /**
     * Handles a pending interaction disambiguation action.
     */
    private void requirePendingInteractionDisambiguation(
            ControlTurnExecutionContext context,
            String nodeId,
            TurnAction action,
            ExecutionState state) {
        String intro = action.userFacingMessage().isBlank()
                ? "我看到了补充信息，但还不能确定要补给哪个等待中的任务。"
                        + "请说明要作用到哪个等待项，或明确说这是一个新任务。"
                : action.userFacingMessage();
        String options = context.envelope().openClarifications().stream()
                .map(request -> "- %s：%s".formatted(
                        request.id(),
                        request.question()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- 当前没有可用等待项");
        state.primaryRecognition = new IntentRecognition(
                IntentType.PENDING_INTERACTION_AMBIGUOUS,
                0.5,
                "TURN_ROUTER",
                "等待交互消歧",
                """
                %s

                可选等待项：
                %s
                """.formatted(intro, options).trim(),
                List.of("pending interaction disambiguation"),
                true,
                false,
                IntentRiskLevel.LOW,
                List.of("pending-interaction"));
        state.immediateAssistantMessage = state.primaryRecognition
                .decisionSummary();
        state.immediateAssistantMessageType = "CLARIFICATION_DISAMBIGUATION";
        state.blockedNodeIds.add(nodeId);
        state.stopAllActions = true;
    }

    /**
     * Handles a clarification-like answer when there is no open target.
     */
    private void clarificationAnswerWithoutTarget(
            String nodeId,
            TurnAction action,
            ExecutionState state) {
        IntentRecognition recognition = action.recognition();
        state.primaryRecognition = new IntentRecognition(
                IntentType.CLARIFICATION_ANSWER,
                recognition == null ? 0.5 : recognition.confidence(),
                recognition == null ? "TURN_ROUTER" : recognition.classifier(),
                recognition == null
                        ? "澄清回答无目标"
                        : recognition.goalSummary(),
                "我理解你是在补充任务信息，但当前没有找到正在等待补充的任务。"
                        + "请重新描述要完成的目标，或先创建一个需要补充信息的任务。",
                recognition == null ? List.of() : recognition.constraints(),
                false,
                false,
                recognition == null ? IntentRiskLevel.LOW : recognition.riskLevel(),
                recognition == null
                        ? List.of("clarification-answer")
                        : recognition.labels());
        state.immediateAssistantMessage = state.primaryRecognition
                .decisionSummary();
        state.immediateAssistantMessageType = "CLARIFICATION_DISAMBIGUATION";
        state.blockedNodeIds.add(nodeId);
        state.stopAllActions = true;
    }

    /**
     * Answers a pending ClarificationRequest and resumes its original target
     * when the structured contract is complete.
     */
    private void answerClarification(
            ControlTurnExecutionContext context,
            String nodeId,
            TurnAction action,
            ExecutionState state) {
        ClarificationRequest clarification = openClarification(
                context.envelope(),
                action.targetId());
        PendingInteractionFacts safeFacts = enrichDefaultConsent(
                action.answerText(),
                action.facts() == null
                ? PendingInteractionFacts.empty()
                : action.facts());
        if (clarification.jobId() == null) {
            throw new RuntimeStateException(
                    "CLARIFICATION_RESUME_TARGET_UNSUPPORTED",
                    "Clarification must be attached to a Job");
        }
        PendingInteractionCompletion completion =
                pendingCompletionPolicy.assess(
                        clarification.question(),
                        clarification.blockingSummary(),
                        clarification.contractJson(),
                        safeFacts,
                        accumulatedClarificationFacts(
                                clarification,
                                context.envelope()));
        if (!completion.complete()) {
            keepClarificationOpen(
                    context,
                    nodeId,
                    action,
                    clarification,
                    completion,
                    state);
            return;
        }
        completeClarification(context, action, clarification, completion, state);
    }

    /**
     * Records a partial answer and keeps the task waiting for more input.
     */
    private void keepClarificationOpen(
            ControlTurnExecutionContext context,
            String nodeId,
            TurnAction action,
            ClarificationRequest clarification,
            PendingInteractionCompletion completion,
            ExecutionState state) {
        JobView waitingJob = jobService.get(clarification.jobId());
        clarificationService.recordPartialAnswer(
                clarification.id(),
                context.userMessageId(),
                action.answerText(),
                new ClarificationResolution(
                        clarification.id(),
                        clarification.sourceType(),
                        clarificationTargetId(clarification),
                        combinedAnswer(clarification.answer(), action.answerText()),
                        completion.mergedFacts(),
                        completion.missingFields(),
                        completion.answerSummary()));
        rememberFacts(context, waitingJob, completion.mergedFacts());
        state.representativeJob = waitingJob;
        conversationStore.linkMessageToJob(context.userMessageId(), waitingJob.id());
        conversationStore.activateJobAndRetitle(
                context.conversation().id(),
                waitingJob.id(),
                waitingJob.goalSummary());
        controlTurnStore.attachJob(context.controlTurnId(), waitingJob.id());
        state.primaryRecognition = clarificationRecognition(
                waitingJob,
                "已记录补充信息，但澄清合同仍缺少："
                        + String.join(",", completion.missingFields()));
        appendImmediateAssistantMessage(
                state,
                waitingJob,
                clarificationResponseRenderer.incomplete(
                        clarification,
                        completion),
                "CLARIFICATION_QUESTION");
        // Only dependency edges should propagate this waiting state. Independent
        // sibling nodes in the same user turn can still be executed.
        state.blockedNodeIds.add(nodeId);
    }

    /**
     * Completes a clarification answer and schedules the original execution
     * boundary for resume or start.
     */
    private void completeClarification(
            ControlTurnExecutionContext context,
            TurnAction action,
            ClarificationRequest clarification,
            PendingInteractionCompletion completion,
            ExecutionState state) {
        String resolvedAnswer = combinedAnswer(
                clarification.answer(),
                action.answerText());
        clarificationService.answer(
                clarification.id(),
                context.userMessageId(),
                resolvedAnswer);
        JobView job;
        if (clarification.taskRunId() != null
                && clarification.loopNodeId() != null) {
            var origin = recoveryStore.requireLoopNodeResumeContext(
                    clarification.loopNodeId());
            // The answer makes the original TaskRun resumable; background
            // execution is submitted after the Control transaction commits.
            runtimeTransactions.resumeAfterHumanAnswer(
                    origin.context(),
                    clarification.id(),
                    resolvedAnswer,
                    completion.mergedFacts(),
                    completion.answerSummary());
            clarificationService.resolve(new ClarificationResolution(
                    clarification.id(),
                    clarification.sourceType(),
                    clarification.loopNodeId(),
                    resolvedAnswer,
                    completion.mergedFacts(),
                    completion.missingFields(),
                    completion.answerSummary()));
            job = jobService.get(clarification.jobId());
            rememberFacts(context, job, completion.mergedFacts());
            state.resumeTaskRunId = clarification.taskRunId();
            state.dispatches.add(ControlDispatchCommand.resume(
                    job,
                    clarification.taskRunId()));
        } else if (clarification.taskId() != null) {
            job = jobService.resumeAfterClarification(
                    clarification.jobId(),
                    clarification.taskId(),
                    resolvedAnswer,
                    factsJson(completion.mergedFacts()),
                    completion.answerSummary());
            clarificationService.resolve(new ClarificationResolution(
                    clarification.id(),
                    clarification.sourceType(),
                    clarification.taskId(),
                    resolvedAnswer,
                    completion.mergedFacts(),
                    completion.missingFields(),
                    completion.answerSummary()));
            rememberFacts(context, job, completion.mergedFacts());
            if (hasReadyTask(job)) {
                state.dispatches.add(ControlDispatchCommand.start(job));
            }
        } else {
            throw new RuntimeStateException(
                    "CLARIFICATION_RESUME_TARGET_UNSUPPORTED",
                    "Clarification target is missing Task or Loop context");
        }
        state.representativeJob = job;
        conversationStore.linkMessageToJob(context.userMessageId(), job.id());
        conversationStore.activateJobAndRetitle(
                context.conversation().id(),
                job.id(),
                job.goalSummary());
        controlTurnStore.attachJob(context.controlTurnId(), job.id());
        state.primaryRecognition = clarificationRecognition(
                job,
                "收到，我已把这条补充信息绑定到对应任务，并会继续处理。");
    }

    /**
     * Creates a synthetic clarification recognition for audit.
     */
    private IntentRecognition clarificationRecognition(
            JobView job,
            String summary) {
        return new IntentRecognition(
                IntentType.CLARIFICATION_ANSWER,
                1.0,
                "TURN_ROUTER",
                job.goalSummary(),
                summary,
                List.of("clarification answered"),
                false,
                false,
                IntentRiskLevel.LOW,
                List.of("clarification-answer"));
    }

    /**
     * Builds the ControlDecision recognition for the full plan.
     */
    private IntentRecognition decisionRecognition(
            TurnRoutingPlan plan,
            ExecutionState state) {
        if (!plan.mixed() && state.primaryRecognition != null) {
            return state.primaryRecognition;
        }
        TurnAction createJob = plan.actions().stream()
                .filter(action -> action.actionType() == TurnActionType.CREATE_JOB)
                .findFirst()
                .orElse(null);
        IntentRecognition base = createJob == null
                ? state.primaryRecognition
                : createJob.recognition();
        IntentType type = base == null
                ? IntentType.CLARIFICATION_ANSWER
                : base.intentType();
        String goal = base == null
                ? "混合控制轮次"
                : base.goalSummary();
        return new IntentRecognition(
                type,
                base == null ? 0.85 : base.confidence(),
                "TURN_ROUTER",
                goal,
                plan.auditSummary(),
                plan.actions().stream()
                        .map(action -> "turn-action:" + action.actionType())
                        .toList(),
                state.immediateAssistantMessage != null,
                plan.mixed(),
                base == null ? IntentRiskLevel.LOW : base.riskLevel(),
                base == null ? List.of("mixed-turn") : base.labels());
    }

    /**
     * Finds an open clarification by ID in the current context snapshot.
     */
    private ClarificationRequest openClarification(
            ContextEnvelope envelope,
            UUID targetId) {
        return envelope.openClarifications().stream()
                .filter(candidate -> candidate.id().equals(targetId))
                .findFirst()
                .orElseThrow(() -> new RuntimeStateException(
                        "CLARIFICATION_MATCH_LOST",
                        "Matched clarification is no longer open"));
    }

    /**
     * Merges facts already captured for the same clarification scope.
     */
    private Map<String, String> accumulatedClarificationFacts(
            ClarificationRequest target,
            ContextEnvelope envelope) {
        Map<String, String> facts = new LinkedHashMap<>();
        if (envelope != null) {
            envelope.conversationFacts().stream()
                    .filter(fact -> relevantClarificationFact(target, fact))
                    .forEach(fact -> putContextFact(
                            facts,
                            fact));
        }
        putResolutionFacts(facts, target.resolutionJson());
        if (envelope != null) {
            envelope.resolvedClarifications().stream()
                    .filter(request -> sameJobAndTask(target, request))
                    .forEach(request -> putResolutionFacts(
                            facts,
                            request.resolutionJson()));
        }
        return Map.copyOf(facts);
    }

    /**
     * Checks whether two clarification records share the same resume scope.
     */
    private boolean sameJobAndTask(
            ClarificationRequest left,
            ClarificationRequest right) {
        if (left.jobId() == null || right.jobId() == null
                || !left.jobId().equals(right.jobId())) {
            return false;
        }
        if (left.taskId() != null && right.taskId() != null) {
            return left.taskId().equals(right.taskId());
        }
        return left.taskRunId() != null
                && left.taskRunId().equals(right.taskRunId());
    }

    /**
     * Checks whether a durable fact may contribute to a clarification target.
     *
     * <p>Job-scoped facts can only satisfy the same Job. Conversation-scoped
     * facts are limited to stable user facts or fields explicitly present in
     * the target contract, preventing sibling task parameters from completing
     * the wrong pending interaction.</p>
     */
    private boolean relevantClarificationFact(
            ClarificationRequest target,
            ContextFact fact) {
        String factScope = normalizeText(fact.scope());
        if (target.jobId() != null
                && factScope.equals(normalizeText("JOB:" + target.jobId()))) {
            return true;
        }
        if (!"conversation".equals(factScope)) {
            return false;
        }
        String key = normalizeText(fact.key());
        return stableUserFact(key) || contractContainsField(target, key);
    }

    /**
     * Checks whether a key is safe to reuse across tasks.
     */
    private boolean stableUserFact(String key) {
        return "name".equals(key)
                || "username".equals(key)
                || "preferredname".equals(key)
                || "nickname".equals(key);
    }

    /**
     * Checks whether the clarification contract mentions a fact key.
     */
    private boolean contractContainsField(
            ClarificationRequest target,
            String key) {
        try {
            JsonNode slots = objectMapper.readTree(
                            target.contractJson() == null
                                    || target.contractJson().isBlank()
                                            ? "{}"
                                            : target.contractJson())
                    .path("slots");
            if (!slots.isArray()) {
                return false;
            }
            for (JsonNode slot : slots) {
                String slotText = normalizeText("%s %s %s".formatted(
                        slot.path("key").asText(""),
                        slot.path("label").asText(""),
                        slot.path("aliases").toString()));
                if (slotText.contains(key)) {
                    return true;
                }
            }
            return false;
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    /**
     * Reads extractedFacts from a ClarificationResolution JSON document.
     */
    private void putResolutionFacts(
            Map<String, String> facts,
            String resolutionJson) {
        if (resolutionJson == null || resolutionJson.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(resolutionJson)
                    .path("extractedFacts");
            if (!node.isObject()) {
                return;
            }
            node.fields().forEachRemaining(entry -> {
                String value = entry.getValue().asText("").trim();
                if (!entry.getKey().isBlank() && !value.isBlank()) {
                    facts.put(entry.getKey(), value);
                }
            });
        } catch (JsonProcessingException ignored) {
            // Broken historical audit JSON must not block a fresh answer.
        }
    }

    /**
     * Writes durable Conversation-level facts for reuse by later tasks.
     */
    private void rememberFacts(
            ControlTurnExecutionContext context,
            JobView job,
            Map<String, String> facts) {
        conversationFactService.rememberJobScopedFacts(
                context.conversation().id(),
                context.userMessageId(),
                "CLARIFICATION_ANSWER",
                job == null ? null : job.id(),
                facts);
    }

    /**
     * Adds userAcceptedDefaults when the raw answer clearly says the user does
     * not want to provide more optional information.
     */
    private PendingInteractionFacts enrichDefaultConsent(
            String answerText,
            PendingInteractionFacts facts) {
        if (!isDefaultConsent(answerText)
                || hasDefaultConsentFact(facts.facts())) {
            return facts;
        }
        Map<String, String> mergedFacts = new LinkedHashMap<>(facts.facts());
        mergedFacts.put("userAcceptedDefaults", "true");
        String summary = facts.answerSummary().isBlank()
                ? "用户接受剩余可默认字段按默认处理"
                : facts.answerSummary() + "；用户接受剩余可默认字段按默认处理";
        return new PendingInteractionFacts(
                mergedFacts,
                facts.missingFields(),
                summary);
    }

    /**
     * Checks whether structured facts already include default consent.
     */
    private boolean hasDefaultConsentFact(Map<String, String> facts) {
        return facts.entrySet().stream()
                .anyMatch(entry -> "useraccepteddefaults".equals(
                        normalizeText(entry.getKey()))
                        && "true".equals(normalizeText(entry.getValue())));
    }

    /**
     * Recognizes natural phrases such as “就这些吧” as default consent.
     */
    private boolean isDefaultConsent(String answerText) {
        String normalized = normalizeText(answerText);
        return normalized.contains("默认即可")
                || normalized.contains("默认")
                || normalized.contains("你看着办")
                || normalized.contains("随意")
                || normalized.contains("随便")
                || normalized.contains("都行")
                || normalized.contains("就这些")
                || normalized.contains("就这样")
                || normalized.contains("没有了")
                || normalized.contains("不补充了")
                || normalized.contains("其他随意");
    }

    /**
     * Normalizes user text for conservative default-consent matching.
     */
    private String normalizeText(String value) {
        return value == null
                ? ""
                : value.replaceAll("\\s+", "")
                        .trim()
                        .toLowerCase(Locale.ROOT);
    }

    /**
     * Adds a ContextFact into the accumulated fact map.
     */
    private void putContextFact(
            Map<String, String> facts,
            ContextFact fact) {
        if (fact.key() != null && !fact.key().isBlank()
                && fact.value() != null && !fact.value().isBlank()) {
            facts.put(fact.key(), fact.value());
        }
    }

    /**
     * Returns the clarification resume target ID.
     */
    private UUID clarificationTargetId(ClarificationRequest clarification) {
        if (clarification.loopNodeId() != null) {
            return clarification.loopNodeId();
        }
        if (clarification.taskId() != null) {
            return clarification.taskId();
        }
        throw new RuntimeStateException(
                "CLARIFICATION_RESUME_TARGET_UNSUPPORTED",
                "Clarification target is missing Task or Loop context");
    }

    /**
     * Merges previous and current natural language answers.
     */
    private String combinedAnswer(
            String previousAnswer,
            String currentAnswer) {
        String current = currentAnswer == null ? "" : currentAnswer.trim();
        if (previousAnswer == null || previousAnswer.isBlank()) {
            return current;
        }
        if (current.isBlank()) {
            return previousAnswer.trim();
        }
        return previousAnswer.trim() + "\n" + current;
    }

    /**
     * Checks whether the Job has a READY task to dispatch.
     */
    private boolean hasReadyTask(JobView job) {
        return job.tasks().stream()
                .anyMatch(task -> task.status() == TaskStatus.READY);
    }

    /**
     * Adds a user-visible immediate message to the current Control turn.
     *
     * <p>A mixed turn can create multiple Jobs that all need clarification.
     * The Conversation message model currently supports one immediate assistant
     * message per turn, so Control aggregates those questions into one message
     * instead of overwriting earlier questions.</p>
     */
    private void appendImmediateAssistantMessage(
            ExecutionState state,
            JobView job,
            String message,
            String messageType) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (state.immediateAssistantMessage == null
                || state.immediateAssistantMessage.isBlank()) {
            state.immediateAssistantMessage = message.trim();
            state.immediateAssistantMessageType = messageType;
            state.immediateAssistantJob = job;
            return;
        }
        state.immediateAssistantMessage = """
                %s

                ---

                %s
                """.formatted(
                        state.immediateAssistantMessage.trim(),
                        message.trim()).trim();
        state.immediateAssistantMessageType = "CLARIFICATION_QUESTION";
        if (state.immediateAssistantJob == null) {
            state.immediateAssistantJob = job;
        }
    }

    /**
     * Serializes Control structured data.
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize control payload",
                    exception);
        }
    }

    /**
     * Serializes extracted facts for Task/Job contracts.
     */
    private String factsJson(Map<String, String> facts) {
        try {
            return objectMapper.writeValueAsString(
                    facts == null ? Map.of() : facts);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize clarification facts",
                    exception);
        }
    }

    /**
     * Mutable execution accumulator scoped to one Control turn.
     */
    private static final class ExecutionState {
        private final List<ControlDispatchCommand> dispatches =
                new ArrayList<>();
        private final Set<String> blockedNodeIds = new HashSet<>();
        private JobView representativeJob;
        private JobView immediateAssistantJob;
        private TaskGraphPlan taskGraph;
        private UUID resumeTaskRunId;
        private String immediateAssistantMessage;
        private String immediateAssistantMessageType;
        private IntentRecognition primaryRecognition;
        private boolean stopAllActions;
    }
}
