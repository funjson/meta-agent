package com.funjson.metaagent.control.application;

import java.util.UUID;

import com.funjson.metaagent.clarification.application.ClarificationService;
import com.funjson.metaagent.job.api.JobView;
import com.funjson.metaagent.job.application.JobService;
import com.funjson.metaagent.task.domain.TaskStatus;
import com.funjson.metaagent.control.api.ChatTurnRequest;
import com.funjson.metaagent.control.api.ChatTurnResult;
import com.funjson.metaagent.conversation.application.AssistantMessageService;
import com.funjson.metaagent.conversation.application.ConversationService;
import com.funjson.metaagent.intent.domain.IntentType;
import org.springframework.stereotype.Service;

/**
 * Control Kernel：编排一轮聊天从控制初始化到任务执行和用户反馈的完整流程。
 */
@Service
public class ControlKernel {

    private final ControlTurnInitializer initializer;
    private final AssistantMessageService assistantMessageService;
    private final ConversationService conversationService;
    private final ControlJobWorker jobWorker;
    private final JobService jobService;
    private final ClarificationService clarificationService;
    private final ControlUserResponseRenderer controlUserResponseRenderer;

    /**
     * 创建聊天编排服务。
     *
     * @param initializer Control 初始化器
     * @param assistantMessageService Assistant 消息事务服务
     * @param conversationService Conversation Service
     * @param jobWorker 后台 Job Worker
     * @param jobService Job Service
     * @param clarificationService 澄清请求服务
     * @param controlUserResponseRenderer Control 层用户响应渲染器
     */
    public ControlKernel(
            ControlTurnInitializer initializer,
            AssistantMessageService assistantMessageService,
            ConversationService conversationService,
            ControlJobWorker jobWorker,
            JobService jobService,
            ClarificationService clarificationService,
            ControlUserResponseRenderer controlUserResponseRenderer) {
        this.initializer = initializer;
        this.assistantMessageService = assistantMessageService;
        this.conversationService = conversationService;
        this.jobWorker = jobWorker;
        this.jobService = jobService;
        this.clarificationService = clarificationService;
        this.controlUserResponseRenderer = controlUserResponseRenderer;
    }

    /**
     * 执行一轮聊天。
     *
     * @param conversationId Conversation ID
     * @param idempotencyKey 幂等键
     * @param request 聊天请求
     * @return 本轮聚合结果
     */
    public ChatTurnResult send(
            UUID conversationId,
            String idempotencyKey,
            ChatTurnRequest request) {
        // Control 初始化事务先持久化用户消息、意图和 Job，Loop 执行不占用该事务。
        var initialization = initializer.initialize(
                conversationId,
                idempotencyKey,
                request);
        if (initialization.immediateAssistantMessage() != null) {
            appendImmediateMessage(conversationId, initialization);
            // 只有用户可见提示、没有后台派发时，本轮已经结束；继续向下会重复渲染 Control 提示。
            if (initialization.dispatches().isEmpty()) {
                return new ChatTurnResult(
                        initialization.controlTurnId(),
                        conversationService.get(conversationId),
                        initialization.decision(),
                        initialization.job() == null
                                ? null
                                : jobService.get(initialization.job().id()),
                        null);
            }
        }
        if (!initialization.dispatches().isEmpty()) {
            submitDispatches(conversationId, initialization);
            return new ChatTurnResult(
                    initialization.controlTurnId(),
                    conversationService.get(conversationId),
                    initialization.decision(),
                    initialization.job() == null
                            ? null
                            : jobService.get(initialization.job().id()),
                    null);
        }
        if (initialization.job() == null) {
            IntentType intentType = IntentType.valueOf(
                    initialization.decision().intentType());
            String messageType = controlUserResponseRenderer.messageType(
                    intentType);
            assistantMessageService.append(
                    conversationId,
                    initialization.userMessage().id(),
                    null,
                    null,
                    controlUserResponseRenderer.messageText(
                            intentType,
                            initialization.decision().decisionSummary()),
                    messageType);
            return new ChatTurnResult(
                    initialization.controlTurnId(),
                    conversationService.get(conversationId),
                    initialization.decision(),
                    null,
                    null);
        }

        JobView job = initialization.job();
        if (initialization.resumeTaskRunId() != null) {
            jobWorker.submitResume(new ControlJobWorker.TaskRunResumeCommand(
                    conversationId,
                    initialization.userMessage().id(),
                    job.id(),
                    initialization.resumeTaskRunId()));
            return new ChatTurnResult(
                    initialization.controlTurnId(),
                    conversationService.get(conversationId),
                    initialization.decision(),
                    jobService.get(job.id()),
                    null);
        }
        if (job.tasks().stream()
                .noneMatch(task -> task.status() == TaskStatus.READY)) {
            var clarification = clarificationService.findOpenByJob(job.id())
                    .orElse(null);
            if (clarification != null) {
                assistantMessageService.append(
                        conversationId,
                        initialization.userMessage().id(),
                        job.id(),
                        null,
                        clarification.question(),
                        "CLARIFICATION_QUESTION");
            }
            return new ChatTurnResult(
                    initialization.controlTurnId(),
                    conversationService.get(conversationId),
                    initialization.decision(),
                    jobService.get(job.id()),
                    null);
        }
        jobWorker.submit(new ControlJobWorker.JobStartCommand(
                conversationId,
                initialization.userMessage().id(),
                job.id(),
                job.version(),
                "chat-run:" + initialization.userMessage().id()));
        return new ChatTurnResult(
                initialization.controlTurnId(),
                conversationService.get(conversationId),
                initialization.decision(),
                jobService.get(job.id()),
                null);
    }

    /**
     * Appends an immediate user-facing message produced by Control.
     */
    private void appendImmediateMessage(
            UUID conversationId,
            ControlTurnInitialization initialization) {
        assistantMessageService.append(
                conversationId,
                initialization.userMessage().id(),
                initialization.job() == null
                        ? null
                        : initialization.job().id(),
                null,
                initialization.immediateAssistantMessage(),
                initialization.immediateAssistantMessageType() == null
                        ? "CLARIFICATION_QUESTION"
                        : initialization.immediateAssistantMessageType());
    }

    /**
     * Submits all background execution requests emitted by a Control turn.
     */
    private void submitDispatches(
            UUID conversationId,
            ControlTurnInitialization initialization) {
        for (ControlDispatchCommand dispatch : initialization.dispatches()) {
            if (dispatch.resume()) {
                jobWorker.submitResume(new ControlJobWorker.TaskRunResumeCommand(
                        conversationId,
                        initialization.userMessage().id(),
                        dispatch.jobId(),
                        dispatch.resumeTaskRunId()));
                continue;
            }
            jobWorker.submit(new ControlJobWorker.JobStartCommand(
                    conversationId,
                    initialization.userMessage().id(),
                    dispatch.jobId(),
                    dispatch.jobVersion(),
                    "chat-run:" + initialization.userMessage().id()
                            + ":" + dispatch.jobId()));
        }
    }

}
