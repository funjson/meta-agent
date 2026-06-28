package com.funjson.metaagent.control.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.funjson.metaagent.job.api.CreateJobRequest;
import com.funjson.metaagent.job.api.JobView;
import com.funjson.metaagent.job.application.JobService;
import com.funjson.metaagent.job.application.TaskGraphTemplateService;
import com.funjson.metaagent.job.application.TaskGraphPlanner;
import com.funjson.metaagent.job.domain.JobCreationContext;
import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.job.domain.TaskGraphPlanningRequest;
import com.funjson.metaagent.context.application.ContextAssembler;
import com.funjson.metaagent.control.api.ChatTurnRequest;
import com.funjson.metaagent.control.api.ControlDecisionView;
import com.funjson.metaagent.control.application.port.out.ControlTurnStore;
import com.funjson.metaagent.control.domain.ControlTurn;
import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.conversation.api.MessageView;
import com.funjson.metaagent.provider.application.port.out.ProviderSecretPort;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.conversation.application.port.out.ConversationStore;
import com.funjson.metaagent.loop.application.RuntimeTransactionService;
import com.funjson.metaagent.recovery.application.port.out.RecoveryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.clarification.application.ClarificationService;
import com.funjson.metaagent.clarification.domain.ClarificationResolution;
import com.funjson.metaagent.clarification.domain.ClarificationRequest;
import com.funjson.metaagent.context.domain.ContextEnvelope;
import com.funjson.metaagent.intent.application.IntentRecognitionService;
import com.funjson.metaagent.intent.application.PendingInteractionCompletionPolicy;
import com.funjson.metaagent.intent.application.PendingInteractionRouter;
import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.intent.domain.IntentRecognitionRequest;
import com.funjson.metaagent.intent.domain.IntentRiskLevel;
import com.funjson.metaagent.intent.domain.IntentType;
import com.funjson.metaagent.intent.domain.PendingInteractionCompletion;
import com.funjson.metaagent.intent.domain.PendingInteractionCandidate;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import com.funjson.metaagent.intent.domain.PendingInteractionRoute;
import com.funjson.metaagent.intent.domain.PendingInteractionRouteType;
import com.funjson.metaagent.intent.domain.PendingInteractionRoutingRequest;
import com.funjson.metaagent.task.api.TaskView;
import com.funjson.metaagent.task.domain.TaskStatus;

/**
 * 在单个聊天轮次初始化事务中持久化 Message、ControlTurn、决策和可选 Job。
 */
@Service
public class ControlTurnInitializer {

    private final ConversationStore conversationStore;
    private final ControlTurnStore controlTurnStore;
    private final IntentRecognitionService intentRecognizer;
    private final TaskGraphPlanner taskGraphPlanner;
    private final TaskGraphTemplateService templateService;
    private final JobService jobService;
    private final ClarificationService clarificationService;
    private final ContextAssembler contextAssembler;
    private final PendingInteractionRouter pendingInteractionRouter;
    private final PendingInteractionCompletionPolicy pendingCompletionPolicy;
    private final RecoveryStore recoveryStore;
    private final RuntimeTransactionService runtimeTransactions;
    private final ProviderSecretPort secretStore;
    private final ObjectMapper objectMapper;

    /**
     * 创建聊天轮次初始化器。
     *
     * @param conversationStore Conversation Store Port
     * @param controlTurnStore ControlTurn Store Port
     * @param intentRecognizer Intent Pipeline
     * @param taskGraphPlanner Control Task Graph Planner
     * @param templateService TaskGraphTemplate 匹配服务
     * @param jobService Job Service
     * @param clarificationService 澄清请求服务
     * @param contextAssembler 上下文装配器
     * @param pendingInteractionRouter 等待交互结构化路由器
     * @param pendingCompletionPolicy 等待交互完整性策略
     * @param secretStore Provider Secret Store
     * @param objectMapper JSON 序列化器
     */
    public ControlTurnInitializer(
            ConversationStore conversationStore,
            ControlTurnStore controlTurnStore,
            IntentRecognitionService intentRecognizer,
            TaskGraphPlanner taskGraphPlanner,
            TaskGraphTemplateService templateService,
            JobService jobService,
            ClarificationService clarificationService,
            ContextAssembler contextAssembler,
            PendingInteractionRouter pendingInteractionRouter,
            PendingInteractionCompletionPolicy pendingCompletionPolicy,
            RecoveryStore recoveryStore,
            RuntimeTransactionService runtimeTransactions,
            ProviderSecretPort secretStore,
            ObjectMapper objectMapper) {
        this.conversationStore = conversationStore;
        this.controlTurnStore = controlTurnStore;
        this.intentRecognizer = intentRecognizer;
        this.taskGraphPlanner = taskGraphPlanner;
        this.templateService = templateService;
        this.jobService = jobService;
        this.clarificationService = clarificationService;
        this.contextAssembler = contextAssembler;
        this.pendingInteractionRouter = pendingInteractionRouter;
        this.pendingCompletionPolicy = pendingCompletionPolicy;
        this.recoveryStore = recoveryStore;
        this.runtimeTransactions = runtimeTransactions;
        this.secretStore = secretStore;
        this.objectMapper = objectMapper;
    }

    /**
     * 初始化一轮可审计的 ControlTurn。
     *
     * @param conversationId Conversation ID
     * @param idempotencyKey 幂等键
     * @param request 聊天请求
     * @return 初始化结果
     */
    @Transactional
    public TurnInitialization initialize(
            UUID conversationId,
            String idempotencyKey,
            ChatTurnRequest request) {
        ConversationView conversation = conversationStore.findConversation(conversationId)
                .orElseThrow(() -> new RuntimeStateException(
                        "CONVERSATION_NOT_FOUND",
                        "Conversation not found: " + conversationId));

        // 幂等重试以 ControlTurn 为准，不能重复写 Message、决策或 Job。
        var existing = controlTurnStore.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            ControlTurn turn = existing.get();
            MessageView userMessage = conversationStore.findMessageById(
                            turn.sourceMessageId())
                    .orElseThrow(() -> new IllegalStateException(
                            "ControlTurn source message is missing"));
            ControlDecisionView decision = controlTurnStore.findDecision(
                            turn.id())
                    .orElseThrow(() -> new IllegalStateException(
                            "ControlDecision is missing"));
            JobView job = turn.jobId() == null
                    ? null
                    : jobService.get(turn.jobId());
            return new TurnInitialization(
                    turn.id(),
                    conversation,
                    userMessage,
                    decision,
                    job,
                    null);
        }

        UUID userMessageId = UUID.randomUUID();
        UUID controlTurnId = UUID.randomUUID();
        String content = request.content().trim();
        conversationStore.insertMessage(
                userMessageId,
                conversationId,
                "USER",
                "TEXT",
                content,
                idempotencyKey,
                null,
                null);
        controlTurnStore.insertTurn(
                controlTurnId,
                conversationId,
                userMessageId,
                idempotencyKey);

        ContextEnvelope envelope = contextAssembler.envelope(conversationId);
        boolean modelClassificationAllowed = allowsModelClassification(
                request.providerId(),
                conversation.defaultProviderId());
        var pendingCandidates = envelope.openClarifications().stream()
                .map(this::toPendingCandidate)
                .toList();
        PendingInteractionRoute pendingRoute = pendingInteractionRouter.route(
                new PendingInteractionRoutingRequest(
                        content,
                        contextAssembler.intentPromptView(envelope),
                        pendingCandidates,
                        modelClassificationAllowed));
        if (pendingRoute.routeType()
                == PendingInteractionRouteType.EXPLAIN_PENDING_REQUIREMENTS
                && pendingRoute.targetId() != null) {
            ClarificationRequest target = envelope.openClarifications()
                    .stream()
                    .filter(candidate -> candidate.id()
                            .equals(pendingRoute.targetId()))
                    .findFirst()
                    .orElse(null);
            if (target != null) {
                return explainPendingRequirements(
                        conversation,
                        controlTurnId,
                        userMessageId,
                        idempotencyKey,
                        target);
            }
        }
        if (pendingRoute.targetsWaitingInteraction()) {
            ClarificationRequest target = envelope.openClarifications()
                    .stream()
                    .filter(candidate -> candidate.id()
                            .equals(pendingRoute.targetId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeStateException(
                            "CLARIFICATION_MATCH_LOST",
                            "Matched clarification is no longer open"));
            return answerClarification(
                    conversation,
                    controlTurnId,
                    userMessageId,
                    idempotencyKey,
                    routeAnswerText(content, pendingRoute),
                    target,
                    pendingRoute.facts(),
                    envelope);
        }
        if (pendingRoute.routeType()
                == PendingInteractionRouteType.AMBIGUOUS) {
            return requirePendingInteractionDisambiguation(
                    conversation,
                    controlTurnId,
                    userMessageId,
                    idempotencyKey,
                    pendingRoute.userFacingMessage().isBlank()
                            ? pendingRoute.auditSummary()
                            : pendingRoute.userFacingMessage(),
                    envelope);
        }

        // 等待交互 Router 未命中时，继续进入常规意图识别。
        var recognition = intentRecognizer.recognize(new IntentRecognitionRequest(
                content,
                contextAssembler.intentPromptView(envelope),
                conversation.activeJobId(),
                modelClassificationAllowed));
        if (recognition.intentType() == IntentType.CLARIFICATION_ANSWER) {
            if (envelope.openClarifications().size() == 1) {
                // 模型判定这是补充回答且只有一个等待项时，可以安全恢复原等待点。
                return answerClarification(
                        conversation,
                        controlTurnId,
                        userMessageId,
                        idempotencyKey,
                        content,
                        envelope.openClarifications().getFirst(),
                        PendingInteractionFacts.empty(),
                        envelope);
            }
            if (!envelope.openClarifications().isEmpty()) {
                return requirePendingInteractionDisambiguation(
                        conversation,
                        controlTurnId,
                        userMessageId,
                        idempotencyKey,
                        "模型判断当前消息像澄清回答，但存在多个等待项，需要先选择目标。",
                        envelope);
            }
            // 这里不能把“像新任务”的澄清回答直接改写为 CREATE_JOB。
            // 混合意图存在多种形态，当前轮只做无等待项保护，正式混合意图路由留到下一轮统一设计。
            return recordClarificationAnswerWithoutTarget(
                    conversation,
                    controlTurnId,
                    userMessageId,
                    idempotencyKey,
                    recognition);
        }
        JobView job = null;
        TaskGraphPlan taskGraph = null;
        com.funjson.metaagent.job.api.TaskGraphTemplateView matchedTemplate = null;
        if (recognition.createsJob()) {
            String providerId = resolveProvider(request.providerId(), conversation.defaultProviderId());
            matchedTemplate = templateService.match(
                    conversation.agentProfileId(),
                    recognition.labels()).orElse(null);
            // 配置模板优先；没有匹配项时才进入受控动态规划。
            taskGraph = matchedTemplate == null
                    ? taskGraphPlanner.plan(
                            new TaskGraphPlanningRequest(
                                    content,
                                    recognition.goalSummary(),
                                    recognition.constraints(),
                                    clarificationQuestion(recognition),
                                    clarificationContractJson(recognition),
                                    recognition.requiresClarification(),
                                    recognition.compoundTask(),
                                    modelClassificationAllowed))
                    : matchedTemplate.graph();
            job = jobService.create(
                    "chat-job:" + userMessageId,
                    new CreateJobRequest(content, providerId),
                    JobCreationContext.root(
                            conversation.agentProfileId(),
                            conversationId,
                            userMessageId,
                            matchedTemplate == null
                                    ? null
                                    : matchedTemplate.id(),
                            matchedTemplate == null
                                    ? null
                                    : matchedTemplate.version()),
                    taskGraph);
            conversationStore.linkMessageToJob(userMessageId, job.id());
            conversationStore.activateJobAndRetitle(
                    conversationId,
                    job.id(),
                    recognition.goalSummary());
            controlTurnStore.attachJob(controlTurnId, job.id());
            registerClarificationIfNeeded(
                    conversationId,
                    job,
                    taskGraph);
        }

        UUID decisionId = UUID.randomUUID();
        controlTurnStore.insertDecision(
                decisionId,
                controlTurnId,
                conversationId,
                userMessageId,
                job == null ? null : job.id(),
                recognition,
                json(recognition.constraints()),
                taskGraph == null ? null : json(taskGraph));
        controlTurnStore.completeTurn(controlTurnId);
        ControlDecisionView decision = controlTurnStore.findDecision(
                        controlTurnId)
                .orElseThrow();

        return new TurnInitialization(
                controlTurnId,
                conversation,
                conversationStore.findMessageByIdempotencyKey(idempotencyKey).orElseThrow(),
                decision,
                job,
                null);
    }

    /**
     * 把打开的澄清请求转换为 Intent 层可匹配的中立候选。
     *
     * @param request 澄清请求
     * @return 等待交互候选
     */
    private PendingInteractionCandidate toPendingCandidate(
            ClarificationRequest request) {
        return new PendingInteractionCandidate(
                request.id(),
                "CLARIFICATION",
                request.jobId(),
                request.taskId(),
                request.taskRunId(),
                request.loopNodeId(),
                request.question(),
                request.blockingSummary(),
                request.contractJson());
    }

    /**
     * 选择要绑定回等待项的回答文本。
     *
     * <p>SELECT_PENDING_INTERACTION 场景下，模型可能从上下文中找回上一条待分配补充；
     * 如果模型没有给出 answerText，则退回当前用户消息，避免写入空回答。</p>
     *
     * @param currentContent 当前用户消息
     * @param route 路由结果
     * @return 可恢复回答文本
     */
    private String routeAnswerText(
            String currentContent,
            PendingInteractionRoute route) {
        return route.answerText().isBlank()
                ? currentContent
                : route.answerText();
    }

    /**
     * 生成“需要消歧”的 ControlTurn 结果，不恢复任何等待 Job。
     *
     * @param conversation Conversation
     * @param controlTurnId ControlTurn ID
     * @param userMessageId 用户消息 ID
     * @param idempotencyKey 用户消息幂等键
     * @param matcherSummary 匹配器摘要
     * @param envelope 上下文 Envelope
     * @return 初始化结果
     */
    private TurnInitialization requirePendingInteractionDisambiguation(
            ConversationView conversation,
            UUID controlTurnId,
            UUID userMessageId,
            String idempotencyKey,
            String matcherSummary,
            ContextEnvelope envelope) {
        String intro = matcherSummary == null || matcherSummary.isBlank()
                ? "我看到了补充信息，但还不能确定要补给哪个等待中的任务。"
                        + "请说明要作用到哪个等待项，或明确说这是一个新任务。"
                : matcherSummary.trim();
        String options = envelope.openClarifications().stream()
                .map(request -> "- %s：%s".formatted(
                        request.id(),
                        request.question()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- 当前没有可用等待项");
        IntentRecognition recognition = new IntentRecognition(
                IntentType.PENDING_INTERACTION_AMBIGUOUS,
                0.5,
                "PENDING_INTERACTION_ROUTER",
                "等待交互消歧",
                """
                %s

                可选等待项：
                %s
                """.formatted(intro, options).trim(),
                java.util.List.of("pending interaction disambiguation"),
                true,
                false,
                IntentRiskLevel.LOW,
                java.util.List.of("pending-interaction"));
        controlTurnStore.insertDecision(
                UUID.randomUUID(),
                controlTurnId,
                conversation.id(),
                userMessageId,
                null,
                recognition,
                json(recognition.constraints()),
                null);
        controlTurnStore.completeTurn(controlTurnId);
        ControlDecisionView decision = controlTurnStore.findDecision(
                        controlTurnId)
                .orElseThrow();
        return new TurnInitialization(
                controlTurnId,
                conversation,
                conversationStore.findMessageByIdempotencyKey(idempotencyKey)
                        .orElseThrow(),
                decision,
                null,
                null);
    }

    /**
     * 记录无法绑定目标的澄清回答，避免误落到控制命令 ACK。
     *
     * @param conversation Conversation
     * @param controlTurnId ControlTurn ID
     * @param userMessageId 用户消息 ID
     * @param idempotencyKey 用户消息幂等键
     * @param recognition 模型识别结果
     * @return 初始化结果
     */
    private TurnInitialization recordClarificationAnswerWithoutTarget(
            ConversationView conversation,
            UUID controlTurnId,
            UUID userMessageId,
            String idempotencyKey,
            IntentRecognition recognition) {
        IntentRecognition rewritten = new IntentRecognition(
                IntentType.CLARIFICATION_ANSWER,
                recognition.confidence(),
                recognition.classifier(),
                recognition.goalSummary(),
                "我理解你是在补充任务信息，但当前没有找到正在等待补充的任务。"
                        + "请重新描述要完成的目标，或先创建一个需要补充信息的任务。",
                recognition.constraints(),
                false,
                false,
                recognition.riskLevel(),
                recognition.labels());
        controlTurnStore.insertDecision(
                UUID.randomUUID(),
                controlTurnId,
                conversation.id(),
                userMessageId,
                null,
                rewritten,
                json(rewritten.constraints()),
                null);
        controlTurnStore.completeTurn(controlTurnId);
        ControlDecisionView decision = controlTurnStore.findDecision(
                        controlTurnId)
                .orElseThrow();
        return new TurnInitialization(
                controlTurnId,
                conversation,
                conversationStore.findMessageByIdempotencyKey(idempotencyKey)
                        .orElseThrow(),
                decision,
                null,
                null);
    }

    /**
     * 将用户消息作为当前 OPEN Clarification 的回答处理。
     *
     * @param conversation Conversation
     * @param controlTurnId ControlTurn ID
     * @param userMessageId 用户消息 ID
     * @param idempotencyKey 幂等键
     * @param answer 用户回答
     * @param clarification 打开的澄清请求
     * @param facts 从回答中抽取的结构化事实
     * @param envelope 当前 Conversation 上下文快照
     * @return 初始化结果
     */
    private TurnInitialization answerClarification(
            ConversationView conversation,
            UUID controlTurnId,
            UUID userMessageId,
            String idempotencyKey,
            String answer,
            ClarificationRequest clarification,
            PendingInteractionFacts facts,
            ContextEnvelope envelope) {
        PendingInteractionFacts safeFacts = facts == null
                ? PendingInteractionFacts.empty()
                : facts;
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
                                envelope));
        if (!completion.complete()) {
            JobView waitingJob = jobService.get(clarification.jobId());
            clarificationService.recordPartialAnswer(
                    clarification.id(),
                    userMessageId,
                    answer,
                    new ClarificationResolution(
                            clarification.id(),
                            clarification.sourceType(),
                            clarificationTargetId(clarification),
                            combinedAnswer(clarification.answer(), answer),
                            completion.mergedFacts(),
                            completion.missingFields(),
                            completion.answerSummary()));
            conversationStore.linkMessageToJob(
                    userMessageId,
                    waitingJob.id());
            conversationStore.activateJobAndRetitle(
                    conversation.id(),
                    waitingJob.id(),
                    waitingJob.goalSummary());
            controlTurnStore.attachJob(
                    controlTurnId,
                    waitingJob.id());
            recordClarificationDecision(
                    conversation,
                    controlTurnId,
                    userMessageId,
                    waitingJob,
                    "已记录补充信息，但澄清合同仍缺少："
                            + String.join(",", completion.missingFields()));
            ControlDecisionView decision = controlTurnStore.findDecision(
                            controlTurnId)
                    .orElseThrow();
            return new TurnInitialization(
                    controlTurnId,
                    conversation,
                    conversationStore.findMessageByIdempotencyKey(
                                    idempotencyKey)
                            .orElseThrow(),
                    decision,
                    waitingJob,
                    null,
                    renderIncompleteClarification(
                            clarification,
                            completion),
                    "CLARIFICATION_QUESTION");
        }
        String resolvedAnswer = combinedAnswer(
                clarification.answer(),
                answer);
        clarificationService.answer(
                clarification.id(),
                userMessageId,
                resolvedAnswer);
        JobView job;
        UUID resumeTaskRunId = null;
        if (clarification.taskRunId() != null
                && clarification.loopNodeId() != null) {
            var origin = recoveryStore.requireLoopNodeResumeContext(
                    clarification.loopNodeId());
            // 用户回答只负责把原 TaskRun 放回可恢复状态，长任务续跑交给后台 Worker。
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
            resumeTaskRunId = clarification.taskRunId();
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
        } else {
            throw new RuntimeStateException(
                    "CLARIFICATION_RESUME_TARGET_UNSUPPORTED",
                    "Clarification target is missing Task or Loop context");
        }
        conversationStore.linkMessageToJob(
                userMessageId,
                job.id());
        conversationStore.activateJobAndRetitle(
                conversation.id(),
                job.id(),
                job.goalSummary());
        controlTurnStore.attachJob(
                controlTurnId,
                job.id());
        recordClarificationDecision(
                conversation,
                controlTurnId,
                userMessageId,
                job,
                "收到，我已把这条补充信息绑定到对应任务，并会继续处理。");
        ControlDecisionView decision = controlTurnStore.findDecision(
                        controlTurnId)
                .orElseThrow();
        return new TurnInitialization(
                controlTurnId,
                conversation,
                conversationStore.findMessageByIdempotencyKey(idempotencyKey)
                        .orElseThrow(),
                decision,
                job,
                resumeTaskRunId);
    }

    /**
     * 记录澄清回答相关的 ControlDecision。
     *
     * @param conversation Conversation
     * @param controlTurnId ControlTurn ID
     * @param userMessageId 用户消息 ID
     * @param job 关联 Job
     * @param summary 可审计摘要
     */
    private void recordClarificationDecision(
            ConversationView conversation,
            UUID controlTurnId,
            UUID userMessageId,
            JobView job,
            String summary) {
        IntentRecognition recognition = new IntentRecognition(
                IntentType.CLARIFICATION_ANSWER,
                1.0,
                "CLARIFICATION_ROUTER",
                job.goalSummary(),
                summary,
                java.util.List.of("clarification answered"),
                false,
                false,
                IntentRiskLevel.LOW,
                java.util.List.of("clarification-answer"));
        controlTurnStore.insertDecision(
                UUID.randomUUID(),
                controlTurnId,
                conversation.id(),
                userMessageId,
                job.id(),
                recognition,
                json(recognition.constraints()),
                null);
        controlTurnStore.completeTurn(controlTurnId);
    }

    /**
     * 合并同一澄清请求已经记录的部分事实。
     *
     * <p>部分回答保持 ClarificationRequest 为 OPEN，因此累积事实主要来自当前
     * OPEN 请求的 resolution_json；同时兼容旧数据中同 Job 已 RESOLVED 的
     * 澄清事实，避免同类等待项重复追问。</p>
     *
     * @param target 当前等待澄清
     * @param envelope Conversation 上下文
     * @return 累计事实
     */
    private Map<String, String> accumulatedClarificationFacts(
            ClarificationRequest target,
            ContextEnvelope envelope) {
        Map<String, String> facts = new LinkedHashMap<>();
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
     * 判断两个澄清请求是否属于同一个恢复范围。
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
     * 从 ClarificationResolution JSON 中读取 extractedFacts。
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
            // 历史审计 JSON 损坏不应阻断新一轮澄清；当前回答仍会被单独评估。
        }
    }

    /**
     * 返回澄清请求恢复目标 ID。
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
     * 合并多轮回答文本，供 Task Contract 和审计记录使用。
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
     * 解释当前等待澄清需要补充哪些内容。
     *
     * @param conversation Conversation
     * @param controlTurnId ControlTurn ID
     * @param userMessageId 用户消息 ID
     * @param idempotencyKey 幂等键
     * @param clarification 当前打开的澄清请求
     * @return 初始化结果
     */
    private TurnInitialization explainPendingRequirements(
            ConversationView conversation,
            UUID controlTurnId,
            UUID userMessageId,
            String idempotencyKey,
            ClarificationRequest clarification) {
        JobView job = jobService.get(clarification.jobId());
        conversationStore.linkMessageToJob(userMessageId, job.id());
        controlTurnStore.attachJob(controlTurnId, job.id());
        IntentRecognition recognition = new IntentRecognition(
                IntentType.PENDING_INTERACTION_HELP,
                0.93,
                "PENDING_INTERACTION_ROUTER",
                job.goalSummary(),
                renderClarificationHelp(clarification),
                java.util.List.of("pending interaction help"),
                false,
                false,
                IntentRiskLevel.LOW,
                java.util.List.of("pending-interaction-help"));
        controlTurnStore.insertDecision(
                UUID.randomUUID(),
                controlTurnId,
                conversation.id(),
                userMessageId,
                job.id(),
                recognition,
                json(recognition.constraints()),
                null);
        controlTurnStore.completeTurn(controlTurnId);
        ControlDecisionView decision = controlTurnStore.findDecision(
                        controlTurnId)
                .orElseThrow();
        return new TurnInitialization(
                controlTurnId,
                conversation,
                conversationStore.findMessageByIdempotencyKey(idempotencyKey)
                        .orElseThrow(),
                decision,
                job,
                null,
                recognition.decisionSummary(),
                "CLARIFICATION_QUESTION");
    }

    /**
     * 基于结构化合同渲染“还需要补什么”的用户可见说明。
     */
    private String renderClarificationHelp(ClarificationRequest request) {
        List<String> slotLabels = contractSlotLabels(
                request.contractJson(),
                true);
        if (slotLabels.isEmpty()) {
            return """
                    可以，我需要补充几类会影响结果的信息：你是谁或你的背景、这份内容用在什么场合、希望正式还是轻松、需要多长，以及有没有必须包含或避免的内容。
                    如果你不想细说，也可以直接说“其他随意”或“按通用模板先写”，我会按默认假设推进。
                    """.trim();
        }
        return """
                可以，我现在主要还需要这些信息：%s。
                如果其中有些你不想细说，可以直接说“其他随意”或“按通用模板先写”，我会用默认假设补齐并继续。
                """.formatted(String.join("、", slotLabels)).trim();
    }

    /**
     * 渲染部分回答后的继续澄清消息。
     */
    private String renderIncompleteClarification(
            ClarificationRequest request,
            PendingInteractionCompletion completion) {
        Map<String, String> labels = contractSlotLabelMap(request.contractJson());
        String missing = completion.missingFields().isEmpty()
                ? "还有一些关键信息"
                : completion.missingFields().stream()
                        .map(field -> clarificationFieldLabel(labels, field))
                        .distinct()
                        .reduce((left, right) -> left + "、" + right)
                        .orElse("还有一些关键信息");
        String known = completion.mergedFacts().isEmpty()
                ? ""
                : "我已经收到你补充的一部分信息。\n";
        return """
                %s还差：%s。
                你可以直接补一句话；如果这些你都想让我按通用方式处理，也可以说“其他随意”。
                """.formatted(known, missing).trim();
    }

    /**
     * 从合同 JSON 中读取槽位标签。
     */
    private List<String> contractSlotLabels(
            String contractJson,
            boolean requiredOnly) {
        return List.copyOf(contractSlotLabelMap(contractJson, requiredOnly)
                .values());
    }

    /**
     * 从合同 JSON 中读取槽位 key 到用户可见标签的映射。
     *
     * @param contractJson 合同 JSON
     * @return 保持合同顺序的标签映射
     */
    private Map<String, String> contractSlotLabelMap(String contractJson) {
        return contractSlotLabelMap(contractJson, false);
    }

    /**
     * 从合同 JSON 中读取槽位 key 到用户可见标签的映射。
     *
     * @param contractJson 合同 JSON
     * @param requiredOnly 是否只返回必填槽位
     * @return 保持合同顺序的标签映射
     */
    private Map<String, String> contractSlotLabelMap(
            String contractJson,
            boolean requiredOnly) {
        try {
            JsonNode slots = objectMapper.readTree(safeJson(contractJson))
                    .path("slots");
            if (!slots.isArray()) {
                return Map.of();
            }
            Map<String, String> labels = new LinkedHashMap<>();
            for (JsonNode slot : slots) {
                if (requiredOnly && !slot.path("required").asBoolean(false)) {
                    continue;
                }
                String key = slot.path("key").asText("").trim();
                String label = slot.path("label").asText(
                        slot.path("key").asText(""));
                if (!key.isBlank() && !label.isBlank()) {
                    labels.put(key, label);
                }
            }
            return labels;
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    /**
     * 把系统字段名转换为用户能读懂的中文标签。
     *
     * <p>结构化合同存在时优先使用合同 label；Loop 运行时自然语言澄清可能还没有合同，
     * 因此需要稳定的兜底字段名，避免把 background、role 等内部字段暴露给用户。</p>
     *
     * @param contractLabels 合同中的 key-label 映射
     * @param field 系统字段名或模型 missingFields 项
     * @return 用户可见中文字段标签
     */
    private String clarificationFieldLabel(
            Map<String, String> contractLabels,
            String field) {
        if (field == null || field.isBlank()) {
            return "关键信息";
        }
        String direct = contractLabels.get(field);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        return fallbackClarificationLabels()
                .getOrDefault(normalizeFieldName(field), field.trim());
    }

    /**
     * 通用澄清字段中文兜底标签。
     *
     * @return 归一化字段名到中文标签的映射
     */
    private Map<String, String> fallbackClarificationLabels() {
        return Map.ofEntries(
                Map.entry("name", "姓名或称呼"),
                Map.entry("username", "姓名或称呼"),
                Map.entry("fullname", "姓名或称呼"),
                Map.entry("background", "背景信息"),
                Map.entry("identity", "身份背景"),
                Map.entry("profile", "个人背景"),
                Map.entry("role", "目标岗位或角色"),
                Map.entry("position", "目标岗位或角色"),
                Map.entry("targetposition", "目标岗位"),
                Map.entry("occupation", "职业或岗位"),
                Map.entry("profession", "职业方向"),
                Map.entry("experience", "工作经验"),
                Map.entry("yearsexperience", "工作年限"),
                Map.entry("education", "教育背景"),
                Map.entry("educationlevel", "学历信息"),
                Map.entry("purpose", "使用场景"),
                Map.entry("usecase", "使用场景"),
                Map.entry("scenario", "使用场景"),
                Map.entry("context", "使用场合"),
                Map.entry("style", "风格偏好"),
                Map.entry("tone", "风格语气"),
                Map.entry("length", "长度要求"),
                Map.entry("wordcount", "字数要求"),
                Map.entry("requirements", "特别要求"),
                Map.entry("mustinclude", "必须包含的内容"),
                Map.entry("mustavoid", "需要避免的内容"),
                Map.entry("skills", "技能特长"),
                Map.entry("contact", "联系方式"),
                Map.entry("phone", "电话"),
                Map.entry("email", "邮箱"),
                Map.entry("city", "所在城市"),
                Map.entry("outputformat", "输出形式"));
    }

    /**
     * 归一化字段名，兼容模型返回的 camelCase、下划线和空格。
     */
    private String normalizeFieldName(String field) {
        return field == null
                ? ""
                : field.replaceAll("[_\\-\\s]+", "")
                        .trim()
                        .toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 返回合法 JSON 文本，避免解析空值。
     */
    private String safeJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    /**
     * 将 Intent 的可审计决策摘要转为用户可直接回答的澄清问题。
     *
     * @param recognition 意图识别结果
     * @return 用户可见问题；无需澄清时返回空字符串
     */
    private String clarificationQuestion(IntentRecognition recognition) {
        if (!recognition.requiresClarification()) {
            return "";
        }
        if (!recognition.clarificationQuestion().isBlank()) {
            return recognition.clarificationQuestion();
        }
        return """
                可以，我需要再确认几件会影响结果的信息：你是谁或你的背景、这份内容用在什么场合、希望正式还是轻松、需要多长，以及有没有必须包含或避免的内容。
                如果你想让我先按通用模板写，也可以直接说“其他随意”或“默认即可”。
                """.trim();
    }

    /**
     * 生成或复用结构化澄清合同。
     *
     * @param recognition 意图识别结果
     * @return 合同 JSON
     */
    private String clarificationContractJson(IntentRecognition recognition) {
        if (!recognition.requiresClarification()) {
            return "{}";
        }
        if (hasContractSlots(recognition.clarificationContractJson())) {
            return recognition.clarificationContractJson();
        }
        return """
                {
                  "version": "v1",
                  "slots": [
                    {"key": "name", "label": "姓名或称呼", "required": true, "defaultable": false, "aliases": ["name", "姓名", "名字", "称呼"]},
                    {"key": "purpose", "label": "使用场景", "required": true, "defaultable": false, "aliases": ["purpose", "useCase", "scenario", "用途", "场景", "场合"]},
                    {"key": "background", "label": "身份或背景", "required": true, "defaultable": true, "aliases": ["background", "role", "occupation", "experience", "背景", "身份", "职业", "岗位", "经验"]},
                    {"key": "style", "label": "风格偏好", "required": true, "defaultable": true, "aliases": ["style", "tone", "风格", "语气"]},
                    {"key": "length", "label": "长度要求", "required": true, "defaultable": true, "aliases": ["length", "wordCount", "长度", "字数", "篇幅"]},
                    {"key": "requirements", "label": "必须包含或避免的内容", "required": false, "defaultable": true, "aliases": ["mustInclude", "mustAvoid", "requirements", "特别要求", "避免", "突出"]},
                    {"key": "outputFormat", "label": "输出形式", "required": false, "defaultable": true, "aliases": ["outputFormat", "format", "输出形式", "形式"]}
                  ],
                  "defaultConsentPhrases": ["默认即可", "你看着办", "其他随意", "按通用模板", "都行"]
                }
                """.trim();
    }

    /**
     * 判断字符串是否是 JSON 对象。
     */
    private boolean hasContractSlots(String value) {
        try {
            JsonNode root = objectMapper.readTree(safeJson(value));
            return root.isObject() && root.path("slots").isArray()
                    && !root.path("slots").isEmpty();
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    /**
     * 去掉模型摘要末尾标点，避免拼接用户可见问题时出现双句号。
     *
     * @param value 候选摘要
     * @return 去掉末尾句读符号后的摘要
     */
    private String stripTerminalPunctuation(String value) {
        return value.replaceAll("[。.!！?？；;：:]+$", "");
    }

    /**
     * 解析当前任务实际使用的 Provider。
     *
     * @param requested 请求级 Provider
     * @param conversationDefault Conversation 默认 Provider
     * @return Provider ID
     */
    private String resolveProvider(String requested, String conversationDefault) {
        String candidate = requested == null || requested.isBlank()
                ? conversationDefault
                : requested.trim();
        if ("auto".equals(candidate)) {
            return secretStore.configured() ? "deepseek" : "fake";
        }
        if (!"fake".equals(candidate) && !"deepseek".equals(candidate)) {
            throw new IllegalArgumentException("Unsupported provider: " + candidate);
        }
        if ("deepseek".equals(candidate) && !secretStore.configured()) {
            throw new RuntimeStateException(
                    "PROVIDER_SECRET_MISSING",
                    "DeepSeek API key is not configured");
        }
        return candidate;
    }

    /**
     * 判断当前聊天请求是否允许使用真实模型进行前置意图分类。
     *
     * @param requested 请求级 Provider
     * @param conversationDefault 对话默认 Provider
     * @return 非 Fake 模式且密钥可用时返回 {@code true}
     */
    private boolean allowsModelClassification(
            String requested,
            String conversationDefault) {
        String candidate = requested == null || requested.isBlank()
                ? conversationDefault
                : requested.trim();
        return !"fake".equals(candidate) && secretStore.configured();
    }

    /**
     * 序列化结构化 Control 数据。
     *
     * @param value 待序列化值
     * @return JSON
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize control constraints", exception);
        }
    }

    /**
     * 序列化抽取事实，供 Job/Task 合同持久化。
     *
     * @param facts 结构化事实
     * @return facts JSON
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
     * 只有 TaskGraph 显式携带 ClarificationDraft 时才创建 WAITING_HUMAN 原因。
     *
     * @param conversationId Conversation ID
     * @param job 已创建 Job
     * @param taskGraph 已验证 TaskGraph
     */
    private void registerClarificationIfNeeded(
            UUID conversationId,
            JobView job,
            TaskGraphPlan taskGraph) {
        taskGraph.clarification().ifPresent(draft -> {
            TaskView waitingTask = job.tasks().stream()
                    .filter(task -> task.status() == TaskStatus.WAITING_HUMAN)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Clarification draft requires a waiting task"));
            // 澄清请求与 WAITING_HUMAN 状态绑定，避免状态反推原因。
            clarificationService.openForTaskGraph(
                    conversationId,
                    job.id(),
                    waitingTask.id(),
                    draft);
        });
    }

    /**
     * Control 初始化阶段的输出。
     *
     * @param controlTurnId ControlTurn ID
     * @param conversation Conversation
     * @param userMessage 用户消息
     * @param decision ControlDecision
     * @param job 可选 Job
     */
    public record TurnInitialization(
            UUID controlTurnId,
            ConversationView conversation,
            MessageView userMessage,
            ControlDecisionView decision,
            JobView job,
            UUID resumeTaskRunId,
            String immediateAssistantMessage,
            String immediateAssistantMessageType) {

        /**
         * 兼容大多数轮次的初始化结果。
         */
        public TurnInitialization(
                UUID controlTurnId,
                ConversationView conversation,
                MessageView userMessage,
                ControlDecisionView decision,
                JobView job,
                UUID resumeTaskRunId) {
            this(
                    controlTurnId,
                    conversation,
                    userMessage,
                    decision,
                    job,
                    resumeTaskRunId,
                    null,
                    null);
        }
    }
}
