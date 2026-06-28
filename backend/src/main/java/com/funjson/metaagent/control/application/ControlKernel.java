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

    /**
     * 创建聊天编排服务。
     *
     * @param initializer Control 初始化器
     * @param assistantMessageService Assistant 消息事务服务
     * @param conversationService Conversation Service
     * @param jobWorker 后台 Job Worker
     * @param jobService Job Service
     * @param clarificationService 澄清请求服务
     */
    public ControlKernel(
            ControlTurnInitializer initializer,
            AssistantMessageService assistantMessageService,
            ConversationService conversationService,
            ControlJobWorker jobWorker,
            JobService jobService,
            ClarificationService clarificationService) {
        this.initializer = initializer;
        this.assistantMessageService = assistantMessageService;
        this.conversationService = conversationService;
        this.jobWorker = jobWorker;
        this.jobService = jobService;
        this.clarificationService = clarificationService;
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
            String messageType = userVisibleMessageType(intentType);
            assistantMessageService.append(
                    conversationId,
                    initialization.userMessage().id(),
                    null,
                    null,
                    userVisibleControlMessage(
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
     * 选择 Control 层无 Job 响应的消息类型。
     *
     * @param intentType 当前意图
     * @return 可见消息类型
     */
    private String userVisibleMessageType(IntentType intentType) {
        if (intentType == IntentType.PENDING_INTERACTION_HELP) {
            return "CLARIFICATION_QUESTION";
        }
        return isControlCommand(intentType)
                ? "CONTROL_COMMAND_ACK"
                : "CLARIFICATION_DISAMBIGUATION";
    }

    /**
     * 渲染 Control 层无 Job 响应，避免把内部控制命令 ACK 误用于澄清回答。
     *
     * @param intentType 当前意图
     * @param decisionSummary 决策摘要或用户可见说明
     * @return 用户可见文本
     */
    private String userVisibleControlMessage(
            IntentType intentType,
            String decisionSummary) {
        if (intentType == IntentType.PENDING_INTERACTION_AMBIGUOUS
                || intentType == IntentType.PENDING_INTERACTION_HELP
                || intentType == IntentType.CLARIFICATION_ANSWER) {
            return decisionSummary;
        }
        if (isControlCommand(intentType)) {
            return "控制命令已记录，但对应的暂停、恢复、取消或状态查询动作"
                    + "还需要后续接入正式命令处理器。";
        }
        return "我已经收到这条消息，但当前没有匹配到可执行任务或等待交互。"
                + "请重新描述目标，或明确说明要继续哪个任务。";
    }

    /**
     * 判断是否是正式控制命令。
     *
     * @param intentType 当前意图
     * @return 是否应使用 CONTROL_COMMAND_ACK
     */
    private boolean isControlCommand(IntentType intentType) {
        return intentType == IntentType.PAUSE_JOB
                || intentType == IntentType.RESUME_JOB
                || intentType == IntentType.CANCEL_JOB
                || intentType == IntentType.QUERY_STATUS;
    }
}
