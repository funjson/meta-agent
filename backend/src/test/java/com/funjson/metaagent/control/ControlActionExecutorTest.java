package com.funjson.metaagent.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.clarification.application.ClarificationService;
import com.funjson.metaagent.clarification.application.ClarificationUserResponseRenderer;
import com.funjson.metaagent.clarification.domain.ClarificationReasonType;
import com.funjson.metaagent.clarification.domain.ClarificationRequest;
import com.funjson.metaagent.clarification.domain.ClarificationResolution;
import com.funjson.metaagent.clarification.domain.ClarificationSourceType;
import com.funjson.metaagent.clarification.domain.ClarificationStatus;
import com.funjson.metaagent.context.application.ConversationFactService;
import com.funjson.metaagent.context.domain.ContextConversation;
import com.funjson.metaagent.context.domain.ContextEnvelope;
import com.funjson.metaagent.control.api.ChatTurnRequest;
import com.funjson.metaagent.control.api.ControlDecisionView;
import com.funjson.metaagent.control.application.ControlActionExecutor;
import com.funjson.metaagent.control.application.ControlDispatchCommand;
import com.funjson.metaagent.control.application.ControlJobInitializationService;
import com.funjson.metaagent.control.application.ControlTurnGraphCompiler;
import com.funjson.metaagent.control.application.ControlTurnExecutionContext;
import com.funjson.metaagent.control.application.port.out.ControlTurnStore;
import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.conversation.api.MessageView;
import com.funjson.metaagent.conversation.application.port.out.ConversationStore;
import com.funjson.metaagent.intent.application.PendingInteractionCompletionPolicy;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import com.funjson.metaagent.intent.domain.TurnAction;
import com.funjson.metaagent.intent.domain.TurnRoutingPlan;
import com.funjson.metaagent.job.api.JobView;
import com.funjson.metaagent.job.application.JobService;
import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.loop.application.RuntimeTransactionService;
import com.funjson.metaagent.recovery.application.port.out.RecoveryStore;
import com.funjson.metaagent.task.api.TaskView;
import com.funjson.metaagent.task.domain.TaskStatus;
import org.junit.jupiter.api.Test;

/**
 * Verifies Control action execution for mixed pending-interaction turns.
 */
class ControlActionExecutorTest {

    @Test
    void continuesSecondPendingAnswerAfterFirstClarificationStaysOpen() {
        UUID conversationId = UUID.randomUUID();
        UUID controlTurnId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        UUID resumeJobId = UUID.randomUUID();
        UUID resumeTaskId = UUID.randomUUID();
        UUID weatherJobId = UUID.randomUUID();
        UUID weatherTaskId = UUID.randomUUID();
        UUID resumeClarificationId = UUID.randomUUID();
        UUID weatherClarificationId = UUID.randomUUID();
        ConversationStore conversationStore = mock(ConversationStore.class);
        ControlTurnStore controlTurnStore = mock(ControlTurnStore.class);
        JobService jobService = mock(JobService.class);
        ClarificationService clarificationService =
                mock(ClarificationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ControlActionExecutor executor = new ControlActionExecutor(
                conversationStore,
                controlTurnStore,
                new ControlTurnGraphCompiler(),
                mock(ControlJobInitializationService.class),
                jobService,
                clarificationService,
                new PendingInteractionCompletionPolicy(),
                mock(ConversationFactService.class),
                mock(RecoveryStore.class),
                mock(RuntimeTransactionService.class),
                new ClarificationUserResponseRenderer(objectMapper),
                objectMapper);
        JobView waitingResumeJob = job(
                resumeJobId,
                "生成个人简历",
                JobStatus.WAITING_HUMAN,
                task(resumeTaskId, TaskStatus.WAITING_HUMAN));
        JobView readyWeatherJob = job(
                weatherJobId,
                "查询今天的天气",
                JobStatus.CREATED,
                task(weatherTaskId, TaskStatus.READY));
        when(jobService.get(resumeJobId)).thenReturn(waitingResumeJob);
        when(jobService.resumeAfterClarification(
                eq(weatherJobId),
                eq(weatherTaskId),
                any(),
                any(),
                any())).thenReturn(readyWeatherJob);
        when(conversationStore.findMessageByIdempotencyKey("idem"))
                .thenReturn(Optional.of(message(userMessageId)));
        when(controlTurnStore.findDecision(controlTurnId))
                .thenReturn(Optional.of(decision(controlTurnId, userMessageId)));

        TurnRoutingPlan plan = new TurnRoutingPlan(
                List.of(
                        TurnAction.answerPending(
                                resumeClarificationId,
                                "我叫冯建松",
                                new PendingInteractionFacts(
                                        Map.of("name", "冯建松"),
                                        List.of(),
                                        "补充姓名"),
                                "回答简历澄清"),
                        TurnAction.answerPending(
                                weatherClarificationId,
                                "北京的今天的就行",
                                new PendingInteractionFacts(
                                        Map.of("location", "北京"),
                                        List.of(),
                                        "补充天气地点"),
                                "回答天气澄清")),
                "用户同时回答两个等待任务");
        var result = executor.execute(
                new ControlTurnExecutionContext(
                        conversation(conversationId),
                        controlTurnId,
                        userMessageId,
                        "idem",
                        "我叫冯建松 北京的今天的就行",
                        new ChatTurnRequest(
                                "我叫冯建松 北京的今天的就行",
                                "fake"),
                        envelope(
                                conversationId,
                                clarification(
                                        resumeClarificationId,
                                        conversationId,
                                        resumeJobId,
                                        resumeTaskId,
                                        resumeContract()),
                                clarification(
                                        weatherClarificationId,
                                        conversationId,
                                        weatherJobId,
                                        weatherTaskId,
                                        weatherContract())),
                        true),
                plan);

        assertThat(result.immediateAssistantMessage())
                .contains("联系方式")
                .contains("求职意向");
        assertThat(result.dispatches())
                .extracting(ControlDispatchCommand::jobId)
                .containsExactly(weatherJobId);
        verify(clarificationService).recordPartialAnswer(
                eq(resumeClarificationId),
                eq(userMessageId),
                eq("我叫冯建松"),
                any(ClarificationResolution.class));
        verify(jobService).resumeAfterClarification(
                eq(weatherJobId),
                eq(weatherTaskId),
                eq("北京的今天的就行"),
                any(),
                any());
    }

    /**
     * Creates a minimal conversation view.
     */
    private ConversationView conversation(UUID conversationId) {
        return new ConversationView(
                conversationId,
                "general-agent",
                "测试",
                "ACTIVE",
                "fake",
                null,
                0,
                Instant.now(),
                Instant.now(),
                List.of());
    }

    /**
     * Creates a minimal context envelope with open clarifications.
     */
    private ContextEnvelope envelope(
            UUID conversationId,
            ClarificationRequest first,
            ClarificationRequest second) {
        return new ContextEnvelope(
                new ContextConversation(
                        conversationId,
                        "测试",
                        null),
                List.of(),
                List.of(),
                List.of(first, second),
                List.of());
    }

    /**
     * Creates a TaskGraph clarification request.
     */
    private ClarificationRequest clarification(
            UUID id,
            UUID conversationId,
            UUID jobId,
            UUID taskId,
            String contractJson) {
        return new ClarificationRequest(
                id,
                conversationId,
                jobId,
                taskId,
                null,
                null,
                ClarificationSourceType.TASK_GRAPH,
                ClarificationReasonType.TASK_CONTRACT_MISSING_INPUT,
                ClarificationStatus.OPEN,
                "请补充必要信息",
                contractJson,
                "",
                null,
                "",
                null,
                "缺少关键输入",
                1,
                0,
                Instant.now(),
                Instant.now());
    }

    /**
     * Resume contract with name already answerable and contact/jobTarget blocking.
     */
    private String resumeContract() {
        return """
                {
                  "slots": [
                    {"key": "name", "label": "姓名", "required": true, "requiredLevel": "BLOCKING", "defaultable": false, "aliases": ["姓名", "名字"]},
                    {"key": "contact", "label": "联系方式", "required": true, "requiredLevel": "BLOCKING", "defaultable": false, "aliases": ["联系方式", "手机", "邮箱"]},
                    {"key": "jobTarget", "label": "求职意向", "required": true, "requiredLevel": "BLOCKING", "defaultable": false, "aliases": ["求职意向", "目标岗位"]}
                  ]
                }
                """;
    }

    /**
     * Weather contract that is complete when location is known.
     */
    private String weatherContract() {
        return """
                {
                  "slots": [
                    {"key": "location", "label": "地点", "required": true, "requiredLevel": "BLOCKING", "defaultable": false, "aliases": ["城市", "地点", "位置"]}
                  ]
                }
                """;
    }

    /**
     * Creates a minimal Job view.
     */
    private JobView job(UUID jobId, String goal, JobStatus status, TaskView task) {
        return new JobView(
                jobId,
                null,
                jobId,
                0,
                goal,
                goal,
                "fake",
                status,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                List.of(task),
                List.of());
    }

    /**
     * Creates a minimal Task view.
     */
    private TaskView task(UUID taskId, TaskStatus status) {
        return new TaskView(
                taskId,
                "task",
                1,
                "任务",
                "目标",
                "GENERAL",
                status,
                "LOOP",
                null,
                null,
                null,
                List.of(),
                0);
    }

    /**
     * Creates a minimal user message.
     */
    private MessageView message(UUID messageId) {
        return new MessageView(
                messageId,
                "USER",
                "TEXT",
                "我叫冯建松 北京的今天的就行",
                null,
                null,
                Instant.now());
    }

    /**
     * Creates a minimal persisted Control decision.
     */
    private ControlDecisionView decision(
            UUID controlTurnId,
            UUID sourceMessageId) {
        return new ControlDecisionView(
                UUID.randomUUID(),
                controlTurnId,
                sourceMessageId,
                null,
                "CLARIFICATION_ANSWER",
                1.0,
                "TEST",
                "混合澄清回答",
                "测试",
                List.of(),
                true,
                true,
                "LOW",
                Instant.now());
    }
}
