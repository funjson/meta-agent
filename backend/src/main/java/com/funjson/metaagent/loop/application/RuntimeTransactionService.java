package com.funjson.metaagent.loop.application;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.loop.domain.ExecutionDerivationRequest;
import com.funjson.metaagent.loop.domain.ExecutionDerivationType;
import com.funjson.metaagent.loop.domain.LoopActionResult;
import com.funjson.metaagent.loop.domain.LoopEvaluation;
import com.funjson.metaagent.loop.domain.LoopExecutionPolicy;
import com.funjson.metaagent.loop.domain.LoopNodeStatus;
import com.funjson.metaagent.loop.domain.LoopNodeStateMachine;
import com.funjson.metaagent.loop.domain.LoopOutcome;
import com.funjson.metaagent.loop.domain.LoopPhaseType;
import com.funjson.metaagent.loop.domain.LoopPlan;
import com.funjson.metaagent.loop.domain.LoopRunParentType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.loop.application.port.out.RuntimeStore;
import com.funjson.metaagent.runtime.domain.ChildJobRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;
import com.funjson.metaagent.prompt.domain.RenderedPrompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理 LoopNode 阶段、Child 派生、Evidence 和 Checkpoint 的短事务。
 *
 * <p>任何外部模型调用都发生在本服务事务之外；服务只负责调用前安全点与调用后事实提交。</p>
 */
@Service
public class RuntimeTransactionService {

    private final RuntimeStore runtimeRepository;
    private final ObjectMapper objectMapper;
    private final LoopNodeStateMachine stateMachine;

    /**
     * 创建 Loop 事务服务。
     *
     * @param runtimeRepository Runtime Repository
     * @param objectMapper JSON 序列化器
     * @param stateMachine LoopNode 状态机
     */
    public RuntimeTransactionService(
            RuntimeStore runtimeRepository,
            ObjectMapper objectMapper,
            LoopNodeStateMachine stateMachine) {
        this.runtimeRepository = runtimeRepository;
        this.objectMapper = objectMapper;
        this.stateMachine = stateMachine;
    }

    /**
     * 保存一个已完成的 LoopNode 内部阶段。
     *
     * @param context 节点上下文
     * @param phaseType 阶段类型
     * @param summary 脱敏摘要
     * @param input 阶段输入引用
     * @param output 阶段输出引用
     * @param eventType 领域事件类型
     */
    @Transactional
    public void recordCompletedPhase(
            RunExecutionContext context,
            LoopPhaseType phaseType,
            String summary,
            Object input,
            Object output,
            String eventType) {
        runtimeRepository.insertCompletedPhase(
                UUID.randomUUID(),
                context.loopNodeId(),
                phaseType,
                summary,
                json(input),
                json(output));
        insertPhaseEvent(context, phaseType, summary, eventType);
    }

    /**
     * 保存 Planning 阶段形成的结构化动作计划。
     *
     * @param context 节点上下文
     * @param plan 动作计划
     */
    @Transactional
    public void recordPlan(
            RunExecutionContext context,
            LoopPlan plan) {
        runtimeRepository.updateLoopNodeDecision(
                context.loopNodeId(),
                plan.actionType().name(),
                json(Map.of(
                        "actionType", plan.actionType(),
                        "completionCriterion", plan.completionCriterion(),
                        "summary", plan.summary(),
                        "maxTokens", plan.maxTokens())));
        recordCompletedPhase(
                context,
                LoopPhaseType.PLANNING,
                plan.summary(),
                Map.of("goal", context.goal()),
                plan,
                "PLAN_CREATED");
    }

    /**
     * 在外部模型调用前创建 ACTION_EXECUTION 阶段和可恢复 Checkpoint。
     *
     * @param context 节点上下文
     * @param prompt 已渲染 Prompt 的审计引用
     * @return 外部动作句柄
     */
    @Transactional
    public ExternalActionHandle startExternalAction(
            RunExecutionContext context,
            RenderedPrompt prompt) {
        UUID phaseId = UUID.randomUUID();
        String summary = "开始执行模型动作，外部调用不占用数据库事务";
        runtimeRepository.insertRunningPhase(
                phaseId,
                context.loopNodeId(),
                LoopPhaseType.ACTION_EXECUTION,
                summary,
                json(Map.of(
                        "provider", context.providerId(),
                        "promptId", prompt.promptId(),
                        "promptVersion", prompt.version(),
                        "promptHash", prompt.contentHash())));
        long eventOffset = insertPhaseEvent(
                context,
                LoopPhaseType.ACTION_EXECUTION,
                summary,
                "LOOP_PHASE_STARTED");

        UUID checkpointId = UUID.randomUUID();
        long checkpointSequence =
                runtimeRepository.nextCheckpointSequence(context.taskRunId());
        String checkpointState = json(Map.ofEntries(
                Map.entry("jobId", context.jobId()),
                Map.entry("taskId", context.taskId()),
                Map.entry("taskRunId", context.taskRunId()),
                Map.entry("loopRunId", context.loopRunId()),
                Map.entry("loopNodeId", context.loopNodeId()),
                Map.entry("nodeStatus", LoopNodeStatus.RUNNING.name()),
                Map.entry(
                        "currentPhase",
                        LoopPhaseType.ACTION_EXECUTION.name()),
                Map.entry("pendingAction", "MODEL_CALL"),
                Map.entry("provider", context.providerId()),
                Map.entry("promptId", prompt.promptId()),
                Map.entry("promptVersion", prompt.version())));
        // Prompt 正文与 Secret 不进入 Checkpoint，只保存版本和哈希引用。
        runtimeRepository.insertCheckpoint(
                checkpointId,
                context.taskRunId(),
                context.loopRunId(),
                context.loopNodeId(),
                checkpointSequence,
                "ACTION_PREPARED",
                checkpointState,
                eventOffset);
        runtimeRepository.updateLatestCheckpoint(
                context.taskRunId(),
                checkpointId);
        return new ExternalActionHandle(phaseId);
    }

    /**
     * 在模型调用返回后完成 ACTION_EXECUTION 阶段。
     *
     * @param context 节点上下文
     * @param handle 外部动作句柄
     * @param actionResult 动作结果
     */
    @Transactional
    public void completeExternalAction(
            RunExecutionContext context,
            ExternalActionHandle handle,
            ModelResponse response) {
        String summary = response.hasToolCalls()
                ? "模型调用完成并返回原生工具调用"
                : response.content() == null || response.content().isBlank()
                ? "模型调用完成，但返回内容为空"
                : "模型调用完成并返回可观察结果";
        runtimeRepository.completePhase(
                handle.phaseId(),
                summary,
                json(Map.of(
                        "provider", response.provider(),
                        "model", response.model(),
                        "finishReason", response.finishReason(),
                        "toolCallCount", response.toolCalls().size(),
                        "reasoningPresent",
                        response.reasoningContent() != null
                                && !response.reasoningContent().isBlank(),
                        "contentPresent",
                        response.content() != null && !response.content().isBlank())));
        insertPhaseEvent(
                context,
                LoopPhaseType.ACTION_EXECUTION,
                summary,
                "ACTION_EXECUTED");
    }

    /**
     * 标记跨外部调用的阶段失败。
     *
     * @param context 节点上下文
     * @param handle 外部动作句柄
     * @param failure 安全处理前的异常
     */
    @Transactional
    public void failExternalAction(
            RunExecutionContext context,
            ExternalActionHandle handle,
            RuntimeException failure) {
        String summary = failure.getClass().getSimpleName()
                + ": external action failed";
        runtimeRepository.failPhase(
                handle.phaseId(),
                summary,
                json(Map.of(
                        "errorType", failure.getClass().getSimpleName())));
        insertPhaseEvent(
                context,
                LoopPhaseType.ACTION_EXECUTION,
                summary,
                "LOOP_PHASE_FAILED");
    }

    /**
     * 恢复时复用已持久化的 ACTION_EXECUTION Phase。
     *
     * @param phaseId Phase ID
     * @return 外部动作句柄
     */
    @Transactional
    public ExternalActionHandle reopenExternalAction(UUID phaseId) {
        runtimeRepository.reopenPhaseForRecovery(phaseId);
        return new ExternalActionHandle(phaseId);
    }

    /**
     * 根据 ADJUST 决策创建 Child LoopNode。
     *
     * @param context 父节点上下文
     * @param evaluation 父节点评估
     * @param policy LoopTree 执行边界
     * @return Child 节点上下文
     */
    @Transactional
    public RunExecutionContext spawnChild(
            RunExecutionContext context,
            LoopEvaluation evaluation,
            LoopExecutionPolicy policy) {
        return spawnChild(
                context,
                ExecutionDerivationRequest.childLoop(
                        "evaluation:"
                                + context.loopNodeId()
                                + ":"
                                + (context.iterationNo() + 1),
                        evaluation.summary(),
                        context.goal(),
                        evaluation.feedback()),
                policy);
    }

    /**
     * 根据结构化派生请求创建 Child LoopNode。
     *
     * @param context 父节点上下文
     * @param request Child Loop 派生请求
     * @param policy LoopTree 执行边界
     * @return Child 节点上下文
     */
    @Transactional
    public RunExecutionContext spawnChild(
            RunExecutionContext context,
            ExecutionDerivationRequest request,
            LoopExecutionPolicy policy) {
        if (request.type() != ExecutionDerivationType.CHILD_LOOP) {
            throw new RuntimeStateException(
                    "INVALID_DERIVATION_TYPE",
                    "Child Loop derivation requires CHILD_LOOP");
        }
        int currentNodeCount =
                runtimeRepository.countLoopNodes(context.loopRunId());
        if (context.depth() >= policy.maxDepth()
                || currentNodeCount >= policy.maxLoopNodes()) {
            throw new RuntimeStateException(
                    "LOOP_POLICY_EXHAUSTED",
                    "LoopTree depth or node budget exhausted");
        }

        LoopNodeStatus parentStatus =
                runtimeRepository.findLoopNodeStatus(context.loopNodeId());
        if (parentStatus == LoopNodeStatus.RUNNING) {
            stateMachine.requireTransition(
                    parentStatus,
                    LoopNodeStatus.WAITING_CHILDREN);
            runtimeRepository.markLoopNodeWaitingChildren(context.loopNodeId());
        } else if (parentStatus != LoopNodeStatus.WAITING_CHILDREN) {
            throw new RuntimeStateException(
                    "INVALID_PARENT_LOOP_STATUS",
                    "Child Loop can only be spawned from RUNNING or WAITING_CHILDREN parent");
        }
        UUID childNodeId = UUID.randomUUID();
        int childDepth = context.depth() + 1;
        int childIteration = context.iterationNo() + 1;
        runtimeRepository.insertLoopNode(
                childNodeId,
                context.loopRunId(),
                context.loopNodeId(),
                childDepth,
                childIteration,
                request.idempotencyKey(),
                context.taskRunId(),
                context.providerId(),
                request.childGoal(),
                json(Map.of(
                        "taskGoal", request.childGoal(),
                        "provider", context.providerId(),
                        "parentLoopNodeId", context.loopNodeId(),
                        "inheritedFeedback", request.feedback())));

        String eventPayload = json(Map.of(
                "parentLoopNodeId", context.loopNodeId(),
                "childLoopNodeId", childNodeId,
                "depth", childDepth,
                "iterationNo", childIteration,
                "reason", request.reason()));
        long eventOffset = runtimeRepository.insertRuntimeEvent(
                UUID.randomUUID(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                "LOOP_NODE",
                childNodeId,
                "CHILD_LOOP_SPAWNED",
                eventPayload);

        UUID checkpointId = UUID.randomUUID();
        long checkpointSequence =
                runtimeRepository.nextCheckpointSequence(context.taskRunId());
        runtimeRepository.insertCheckpoint(
                checkpointId,
                context.taskRunId(),
                context.loopRunId(),
                childNodeId,
                checkpointSequence,
                "CHILD_LOOP_CREATED",
                json(Map.of(
                        "loopRunId", context.loopRunId(),
                        "parentLoopNodeId", context.loopNodeId(),
                        "loopNodeId", childNodeId,
                        "nodeStatus", LoopNodeStatus.RUNNING.name(),
                        "currentPhase", LoopPhaseType.CONTEXT_BUILD.name(),
                        "inheritedFeedback", request.feedback())),
                eventOffset);
        runtimeRepository.updateLatestCheckpoint(
                context.taskRunId(),
                checkpointId);

        return new RunExecutionContext(
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                context.loopRunId(),
                childNodeId,
                context.loopNodeId(),
                childDepth,
                childIteration,
                context.loopRunParentType(),
                context.loopRunParentId(),
                context.recursionDepth(),
                context.providerId(),
                request.childGoal(),
                request.feedback(),
                null);
    }

    /**
     * 查询当前 LoopRun 节点数。
     *
     * @param loopRunId LoopRun ID
     * @return 节点数
     */
    @Transactional(readOnly = true)
    public int nodeCount(UUID loopRunId) {
        return runtimeRepository.countLoopNodes(loopRunId);
    }

    /**
     * 完成由父模型决策派生出来的单工具 Child LoopNode。
     *
     * <p>该节点只提交自己的 Observation 和局部完成事件，不推进 LoopRun/TaskRun；
     * 父 LoopNode 会在聚合所有工具 Observation 后继续执行正式验收。</p>
     *
     * @param context Child LoopNode 上下文
     * @param actionResult 工具调用结果
     */
    @Transactional
    public void completeChildLoopNode(
            RunExecutionContext context,
            LoopActionResult actionResult) {
        recordCompletedPhase(
                context,
                LoopPhaseType.OBSERVATION,
                "子 LoopNode 已记录单个工具 Observation",
                Map.of(
                        "actionType", actionResult.actionType().name(),
                        "source", actionResult.source()),
                Map.of(
                        "contentPresent",
                        actionResult.content() != null
                                && !actionResult.content().isBlank(),
                        "attributes", actionResult.attributes()),
                "OBSERVATION_RECORDED");
        recordCompletedPhase(
                context,
                LoopPhaseType.EVALUATION,
                "单工具子节点完成，等待父 LoopNode 聚合验收",
                Map.of("scope", "TOOL_CHILD_LOOP_NODE"),
                Map.of(
                        "decision", "COMPLETE_CHILD_ONLY",
                        "parentLoopNodeId",
                        context.parentNodeId() == null
                                ? "ROOT"
                                : context.parentNodeId().toString()),
                "EVALUATION_RECORDED");

        String observation = json(Map.of(
                "status", "SUCCESS",
                "summary", "Tool child LoopNode completed",
                "actionType", actionResult.actionType().name(),
                "source", actionResult.source()));
        String output = json(Map.of(
                "content", actionResult.content() == null
                        ? ""
                        : actionResult.content(),
                "actionType", actionResult.actionType().name(),
                "source", actionResult.source(),
                "attributes", actionResult.attributes()));

        LoopNodeStatus currentStatus =
                runtimeRepository.findLoopNodeStatus(context.loopNodeId());
        stateMachine.requireTransition(
                currentStatus,
                LoopNodeStatus.COMPLETED);
        runtimeRepository.completeLoopNode(
                context.loopNodeId(),
                observation,
                output);
        long eventOffset = runtimeRepository.insertRuntimeEvent(
                UUID.randomUUID(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                "LOOP_NODE",
                context.loopNodeId(),
                "LOOP_NODE_COMPLETED",
                json(Map.of(
                        "loopRunId", context.loopRunId(),
                        "loopNodeId", context.loopNodeId(),
                        "parentLoopNodeId",
                        context.parentNodeId() == null
                                ? "ROOT"
                                : context.parentNodeId().toString(),
                        "childCompletionScope", "TOOL_CALL")));

        UUID checkpointId = UUID.randomUUID();
        runtimeRepository.insertCheckpoint(
                checkpointId,
                context.taskRunId(),
                context.loopRunId(),
                context.loopNodeId(),
                runtimeRepository.nextCheckpointSequence(context.taskRunId()),
                "CHILD_LOOP_TOOL_COMPLETE",
                json(Map.of(
                        "loopRunId", context.loopRunId(),
                        "loopNodeId", context.loopNodeId(),
                        "parentLoopNodeId",
                        context.parentNodeId() == null
                                ? "ROOT"
                                : context.parentNodeId().toString(),
                        "nodeStatus", LoopNodeStatus.COMPLETED.name(),
                        "currentPhase", LoopPhaseType.EVALUATION.name())),
                eventOffset);
        runtimeRepository.updateLatestCheckpoint(
                context.taskRunId(),
                checkpointId);
    }

    /**
     * 子 Loop 或 Child Job 完成后恢复 origin LoopNode。
     *
     * @param context origin LoopNode 上下文
     */
    @Transactional
    public void resumeAfterChildExecution(RunExecutionContext context) {
        LoopNodeStatus currentStatus =
                runtimeRepository.findLoopNodeStatus(context.loopNodeId());
        stateMachine.requireTransition(
                currentStatus,
                LoopNodeStatus.RUNNING);
        runtimeRepository.resumeLoopNode(context.loopNodeId());
        if (currentStatus == LoopNodeStatus.WAITING_CHILD_JOB) {
            runtimeRepository.resumeTaskRunFromChildJob(
                    context.taskRunId());
        }
        runtimeRepository.insertRuntimeEvent(
                UUID.randomUUID(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                "LOOP_NODE",
                context.loopNodeId(),
                "LOOP_NODE_RESUMED",
                json(Map.of(
                        "loopRunId", context.loopRunId(),
                        "loopNodeId", context.loopNodeId(),
                        "resumedFrom", currentStatus.name())));
    }

    /**
     * 原子记录 Child Job 请求，并把父 LoopNode/TaskRun 置为等待态。
     *
     * @param context origin LoopNode 上下文
     * @param request Child Job 请求
     * @return 等待上层物化的 Loop Outcome
     */
    /**
     * 原子挂起当前 LoopNode，等待用户补充澄清信息。
     *
     * @param context origin LoopNode 上下文
     * @param clarificationRequestId 澄清请求 ID
     * @param question 展示给用户的问题
     * @return 等待人工回答的 Loop Outcome
     */
    @Transactional
    public LoopOutcome suspendForClarification(
            RunExecutionContext context,
            UUID clarificationRequestId,
            String question) {
        LoopNodeStatus currentStatus =
                runtimeRepository.findLoopNodeStatus(context.loopNodeId());
        stateMachine.requireTransition(
                currentStatus,
                LoopNodeStatus.WAITING_HUMAN);

        // LoopNode 与 TaskRun 必须同事务进入 WAITING_HUMAN，避免 Worker 继续领取。
        runtimeRepository.markLoopNodeWaitingHuman(context.loopNodeId());
        runtimeRepository.markTaskRunWaitingHuman(context.taskRunId());
        String payload = json(Map.of(
                "jobId", context.jobId(),
                "taskId", context.taskId(),
                "taskRunId", context.taskRunId(),
                "loopRunId", context.loopRunId(),
                "loopNodeId", context.loopNodeId(),
                "clarificationRequestId", clarificationRequestId,
                "question", question));
        long eventOffset = runtimeRepository.insertRuntimeEvent(
                UUID.randomUUID(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                "LOOP_NODE",
                context.loopNodeId(),
                "CLARIFICATION_REQUESTED",
                payload);
        UUID checkpointId = UUID.randomUUID();
        runtimeRepository.insertCheckpoint(
                checkpointId,
                context.taskRunId(),
                context.loopRunId(),
                context.loopNodeId(),
                runtimeRepository.nextCheckpointSequence(
                        context.taskRunId()),
                "CLARIFICATION_REQUESTED",
                payload,
                eventOffset);
        runtimeRepository.updateLatestCheckpoint(
                context.taskRunId(),
                checkpointId);
        return LoopOutcome.waitingHuman(context);
    }

    /**
     * 用户回答后把原始 LoopNode 放回可恢复状态，并写入新的恢复游标。
     *
     * @param context origin LoopNode 上下文
     * @param clarificationRequestId 澄清请求 ID
     * @param answer 用户回答
     */
    @Transactional
    public void resumeAfterHumanAnswer(
            RunExecutionContext context,
            UUID clarificationRequestId,
            String answer) {
        resumeAfterHumanAnswer(
                context,
                clarificationRequestId,
                answer,
                Map.of(),
                "");
    }

    /**
     * 用户回答后把原始 LoopNode 放回可恢复状态，并写入结构化恢复游标。
     *
     * @param context origin LoopNode 上下文
     * @param clarificationRequestId 澄清请求 ID
     * @param answer 用户回答
     * @param extractedFacts 从回答中抽取的结构化事实
     * @param answerSummary 系统审计摘要
     */
    @Transactional
    public void resumeAfterHumanAnswer(
            RunExecutionContext context,
            UUID clarificationRequestId,
            String answer,
            Map<String, String> extractedFacts,
            String answerSummary) {
        LoopNodeStatus currentStatus =
                runtimeRepository.findLoopNodeStatus(context.loopNodeId());
        stateMachine.requireTransition(
                currentStatus,
                LoopNodeStatus.RUNNING);
        runtimeRepository.resumeLoopNode(context.loopNodeId());
        runtimeRepository.resumeTaskRunFromHuman(context.taskRunId());
        String payload = json(Map.of(
                "jobId", context.jobId(),
                "taskId", context.taskId(),
                "taskRunId", context.taskRunId(),
                "loopRunId", context.loopRunId(),
                "loopNodeId", context.loopNodeId(),
                "clarificationRequestId", clarificationRequestId,
                "answer", answer,
                "extractedFacts",
                extractedFacts == null ? Map.of() : extractedFacts,
                "answerSummary",
                answerSummary == null ? "" : answerSummary));
        long eventOffset = runtimeRepository.insertRuntimeEvent(
                UUID.randomUUID(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                "LOOP_NODE",
                context.loopNodeId(),
                "CLARIFICATION_ANSWERED",
                payload);
        UUID checkpointId = UUID.randomUUID();
        runtimeRepository.insertCheckpoint(
                checkpointId,
                context.taskRunId(),
                context.loopRunId(),
                context.loopNodeId(),
                runtimeRepository.nextCheckpointSequence(
                        context.taskRunId()),
                "CLARIFICATION_ANSWERED",
                payload,
                eventOffset);
        runtimeRepository.updateLatestCheckpoint(
                context.taskRunId(),
                checkpointId);
    }

    /**
     * 原子记录 Child Job 请求，并把 origin LoopNode/TaskRun 置为等待态。
     *
     * @param context origin LoopNode 上下文
     * @param request Child Job 请求
     * @return 等待 Job 层物化 Child Job 的 Outcome
     */
    @Transactional
    public LoopOutcome suspendForChildJob(
            RunExecutionContext context,
            ChildJobRequest request) {
        LoopNodeStatus currentStatus =
                runtimeRepository.findLoopNodeStatus(context.loopNodeId());
        stateMachine.requireTransition(
                currentStatus,
                LoopNodeStatus.WAITING_CHILD_JOB);

        // 父节点和 TaskRun 必须在同一事务中进入等待态，避免 Worker 误领。
        runtimeRepository.markLoopNodeWaitingChildJob(
                context.loopNodeId());
        runtimeRepository.markTaskRunWaitingChildJob(
                context.taskRunId());
        String payload = json(Map.of(
                "jobId", context.jobId(),
                "taskId", context.taskId(),
                "taskRunId", context.taskRunId(),
                "loopRunId", context.loopRunId(),
                "loopNodeId", context.loopNodeId(),
                "idempotencyKey", request.idempotencyKey(),
                "goal", request.goal()));
        long eventOffset = runtimeRepository.insertRuntimeEvent(
                UUID.randomUUID(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                "LOOP_NODE",
                context.loopNodeId(),
                "CHILD_JOB_REQUESTED",
                payload);
        UUID checkpointId = UUID.randomUUID();
        runtimeRepository.insertCheckpoint(
                checkpointId,
                context.taskRunId(),
                context.loopRunId(),
                context.loopNodeId(),
                runtimeRepository.nextCheckpointSequence(
                        context.taskRunId()),
                "CHILD_JOB_REQUESTED",
                payload,
                eventOffset);
        runtimeRepository.updateLatestCheckpoint(
                context.taskRunId(),
                checkpointId);
        return LoopOutcome.waitingChildJob(context, request);
    }

    /**
     * 完成叶子 LoopNode、等待它的祖先节点以及 LoopRun/TaskRun。
     *
     * @param context 当前叶子节点上下文
     * @param response 模型响应
     * @param evaluation 完成评估
     * @return 提交给上层 Job/Task 协调器的 Outcome
     */
    @Transactional
    public LoopOutcome complete(
            RunExecutionContext context,
            LoopActionResult actionResult,
            LoopEvaluation evaluation) {
        UUID evidenceId = UUID.randomUUID();
        String observation = json(Map.of(
                "status", "SUCCESS",
                "summary", "Evaluation accepted the action result",
                "actionType", actionResult.actionType().name(),
                "source", actionResult.source(),
                "evaluation", evaluation.summary()));
        String output = json(Map.of(
                "content", actionResult.content(),
                "actionType", actionResult.actionType().name(),
                "source", actionResult.source(),
                "attributes", actionResult.attributes()));

        LoopNodeStatus currentStatus =
                runtimeRepository.findLoopNodeStatus(context.loopNodeId());
        stateMachine.requireTransition(
                currentStatus,
                LoopNodeStatus.COMPLETED);
        runtimeRepository.completeLoopNode(
                context.loopNodeId(),
                observation,
                output);
        stateMachine.requireTransition(
                LoopNodeStatus.WAITING_CHILDREN,
                LoopNodeStatus.COMPLETED);
        runtimeRepository.completeWaitingLoopNodes(
                context.loopRunId(),
                json(Map.of(
                        "status", "SUCCESS",
                        "summary", "Child LoopNode satisfied the parent goal",
                        "completedByChild", context.loopNodeId())),
                output);
        runtimeRepository.insertEvidence(
                evidenceId,
                context.taskId(),
                context.taskRunId(),
                context.loopRunId(),
                context.loopNodeId(),
                actionResult.actionType().name() + "_RESULT_ACCEPTED",
                "loop-node:" + context.loopNodeId(),
                json(Map.of(
                        "completionPolicy", "requireEvidence",
                        "actionType", actionResult.actionType().name(),
                        "source", actionResult.source(),
                        "attributes", actionResult.attributes(),
                        "evaluation", evaluation.summary())));

        String eventPayload = json(Map.of(
                "jobId", context.jobId(),
                "taskId", context.taskId(),
                "taskRunId", context.taskRunId(),
                "loopNodeId", context.loopNodeId(),
                "evidenceId", evidenceId,
                "result", "PASS"));
        long eventOffset = runtimeRepository.insertRuntimeEvent(
                UUID.randomUUID(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                "LOOP_NODE",
                context.loopNodeId(),
                "LOOP_NODE_COMPLETED",
                eventPayload);

        UUID checkpointId = UUID.randomUUID();
        long checkpointSequence =
                runtimeRepository.nextCheckpointSequence(context.taskRunId());
        runtimeRepository.insertCheckpoint(
                checkpointId,
                context.taskRunId(),
                context.loopRunId(),
                context.loopNodeId(),
                checkpointSequence,
                "NODE_COMPLETE",
                json(Map.of(
                        "jobId", context.jobId(),
                        "taskId", context.taskId(),
                        "taskRunId", context.taskRunId(),
                        "loopRunId", context.loopRunId(),
                        "loopNodeId", context.loopNodeId(),
                        "nodeStatus", LoopNodeStatus.COMPLETED.name(),
                        "currentPhase", LoopPhaseType.EVALUATION.name(),
                        "evidenceId", evidenceId,
                        "pendingActions", 0)),
                eventOffset);
        runtimeRepository.updateLatestCheckpoint(
                context.taskRunId(),
                checkpointId);

        // Loop 只完成运行对象；Task/Job 终态仍由 JobCompletionCoordinator 决定。
        runtimeRepository.completeLoopRun(context.loopRunId());
        if (context.loopRunParentType() == LoopRunParentType.TASK_RUN) {
            runtimeRepository.completeTaskRun(
                    context.taskRunId(),
                    actionResult.content());
        }
        return LoopOutcome.completed(
                context,
                actionResult.content(),
                evidenceId);
    }

    /**
     * 记录 LoopTree 失败结果。
     *
     * @param context 当前节点上下文
     * @param failure 安全处理前的异常
     * @return 失败 Outcome
     */
    @Transactional
    public LoopOutcome fail(
            RunExecutionContext context,
            RuntimeException failure) {
        String safeSummary = failure.getClass().getSimpleName()
                + ": loop execution failed";
        String observation = json(Map.of(
                "status", "FAILED",
                "errorType", failure.getClass().getSimpleName(),
                "summary", safeSummary));
        LoopNodeStatus currentStatus =
                runtimeRepository.findLoopNodeStatus(context.loopNodeId());
        stateMachine.requireTransition(
                currentStatus,
                LoopNodeStatus.FAILED);
        runtimeRepository.failLoop(
                context.loopRunId(),
                context.loopNodeId(),
                observation);
        if (context.loopRunParentType() == LoopRunParentType.TASK_RUN) {
            runtimeRepository.failTaskRun(
                    context.taskRunId(),
                    safeSummary);
        }
        return LoopOutcome.failed(context, safeSummary);
    }

    /**
     * 插入阶段完成事件。
     *
     * @param context 节点上下文
     * @param phaseType 阶段
     * @param summary 摘要
     * @param eventType 事件类型
     * @return 事件序号
     */
    private long insertPhaseEvent(
            RunExecutionContext context,
            LoopPhaseType phaseType,
            String summary,
            String eventType) {
        return runtimeRepository.insertRuntimeEvent(
                UUID.randomUUID(),
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                "LOOP_NODE",
                context.loopNodeId(),
                eventType,
                json(Map.of(
                        "loopRunId", context.loopRunId(),
                        "loopNodeId", context.loopNodeId(),
                        "phaseType", phaseType.name(),
                        "summary", summary,
                        "depth", context.depth(),
                        "iterationNo", context.iterationNo())));
    }

    /**
     * 序列化运行状态。
     *
     * @param value 状态值
     * @return JSON
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(
                    value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize runtime state",
                    exception);
        }
    }

    /**
     * 标识一个已提交但尚未完成的外部动作阶段。
     *
     * @param phaseId 阶段记录 ID
     */
    public record ExternalActionHandle(UUID phaseId) {
    }
}
