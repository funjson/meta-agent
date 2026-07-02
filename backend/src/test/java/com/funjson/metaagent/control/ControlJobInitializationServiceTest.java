package com.funjson.metaagent.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.clarification.application.ClarificationService;
import com.funjson.metaagent.control.api.ChatTurnRequest;
import com.funjson.metaagent.control.application.ControlJobInitializationService;
import com.funjson.metaagent.control.application.JobInitializationSpec;
import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.intent.domain.IntentRiskLevel;
import com.funjson.metaagent.intent.domain.IntentType;
import com.funjson.metaagent.intent.domain.TurnTaskType;
import com.funjson.metaagent.job.api.CreateJobRequest;
import com.funjson.metaagent.job.api.JobView;
import com.funjson.metaagent.job.application.DefaultResearchTaskGraphFactory;
import com.funjson.metaagent.job.application.JobService;
import com.funjson.metaagent.job.application.TaskGraphPlanner;
import com.funjson.metaagent.job.application.TaskGraphTemplateService;
import com.funjson.metaagent.job.domain.JobCreationContext;
import com.funjson.metaagent.job.domain.JobStatus;
import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.job.domain.TaskGraphPlanningRequest;
import com.funjson.metaagent.provider.application.ModelCatalogService;
import com.funjson.metaagent.provider.application.ProviderConfigService;
import com.funjson.metaagent.provider.application.port.out.ProviderSecretPort;
import com.funjson.metaagent.provider.domain.ModelCapabilities;
import com.funjson.metaagent.provider.domain.ModelSpec;
import com.funjson.metaagent.task.api.TaskView;
import com.funjson.metaagent.task.domain.TaskStatus;
import org.junit.jupiter.api.Test;

/**
 * Verifies Control-level Job initialization policy normalization.
 */
class ControlJobInitializationServiceTest {

    @Test
    void softensResumeContactAndTargetForLowRiskGenerationContracts()
            throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TaskGraphPlanner planner = mock(TaskGraphPlanner.class);
        JobService jobService = mock(JobService.class);
        ModelCatalogService modelCatalog = mock(ModelCatalogService.class);
        TaskGraphTemplateService templateService =
                mock(TaskGraphTemplateService.class);
        AtomicReference<TaskGraphPlanningRequest> captured =
                new AtomicReference<>();
        when(templateService.match(any(), any())).thenReturn(Optional.empty());
        when(planner.plan(any())).thenAnswer(invocation -> {
            TaskGraphPlanningRequest request = invocation.getArgument(0);
            captured.set(request);
            return TaskGraphPlan.waiting(
                    request.goalSummary(),
                    "CLARIFICATION_REQUIRED",
                    "缺少简历信息",
                    request.clarificationQuestion(),
                    request.clarificationContractJson());
        });
        ModelSpec fakeSpec = new ModelSpec(
                "fake",
                "Fake",
                "fake",
                "fake",
                "fake",
                4096,
                List.of("text"),
                new ModelCapabilities(false, false, false, false, false));
        when(modelCatalog.find("fake")).thenReturn(Optional.of(fakeSpec));
        when(modelCatalog.require("fake")).thenReturn(fakeSpec);
        when(jobService.create(
                any(),
                any(CreateJobRequest.class),
                any(JobCreationContext.class),
                any(TaskGraphPlan.class))).thenReturn(waitingJob());
        ControlJobInitializationService service =
                new ControlJobInitializationService(
                        planner,
                        templateService,
                        mock(DefaultResearchTaskGraphFactory.class),
                        jobService,
                        mock(ClarificationService.class),
                        modelCatalog,
                        mock(ProviderConfigService.class),
                        mock(ProviderSecretPort.class),
                        objectMapper);

        service.initializeRootJob(
                conversation(),
                UUID.randomUUID(),
                "帮我生成一份个人简历",
                new ChatTurnRequest("帮我生成一份个人简历", "fake"),
                resumeRecognition(),
                true);

        JsonNode slots = objectMapper.readTree(
                captured.get().clarificationContractJson()).path("slots");
        assertThat(slot(slots, "name").path("requiredLevel").asText())
                .isEqualTo("BLOCKING");
        assertThat(slot(slots, "contact").path("requiredLevel").asText())
                .isEqualTo("SOFT");
        assertThat(slot(slots, "contact").path("defaultable").asBoolean())
                .isTrue();
        assertThat(slot(slots, "jobTarget").path("requiredLevel").asText())
                .isEqualTo("SOFT");
        assertThat(slot(slots, "jobTarget").path("defaultable").asBoolean())
                .isTrue();
        assertThat(slot(slots, "education").path("requiredLevel").asText())
                .isEqualTo("SOFT");
        assertThat(slot(slots, "work_experience").path("requiredLevel").asText())
                .isEqualTo("SOFT");
    }

    @Test
    void weatherClarificationUsesLocationContractInsteadOfGenerationContract()
            throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TaskGraphPlanner planner = mock(TaskGraphPlanner.class);
        JobService jobService = mock(JobService.class);
        ModelCatalogService modelCatalog = mock(ModelCatalogService.class);
        TaskGraphTemplateService templateService =
                mock(TaskGraphTemplateService.class);
        AtomicReference<TaskGraphPlanningRequest> captured =
                new AtomicReference<>();
        when(templateService.match(any(), any())).thenReturn(Optional.empty());
        when(planner.plan(any())).thenAnswer(invocation -> {
            TaskGraphPlanningRequest request = invocation.getArgument(0);
            captured.set(request);
            return TaskGraphPlan.waiting(
                    request.goalSummary(),
                    "CLARIFICATION_REQUIRED",
                    "缺少天气查询地点",
                    request.clarificationQuestion(),
                    request.clarificationContractJson());
        });
        ModelSpec fakeSpec = new ModelSpec(
                "fake",
                "Fake",
                "fake",
                "fake",
                "fake",
                4096,
                List.of("text"),
                new ModelCapabilities(false, false, false, false, false));
        when(modelCatalog.find("fake")).thenReturn(Optional.of(fakeSpec));
        when(modelCatalog.require("fake")).thenReturn(fakeSpec);
        when(jobService.create(
                any(),
                any(CreateJobRequest.class),
                any(JobCreationContext.class),
                any(TaskGraphPlan.class))).thenReturn(waitingJob());
        ControlJobInitializationService service =
                new ControlJobInitializationService(
                        planner,
                        templateService,
                        mock(DefaultResearchTaskGraphFactory.class),
                        jobService,
                        mock(ClarificationService.class),
                        modelCatalog,
                        mock(ProviderConfigService.class),
                        mock(ProviderSecretPort.class),
                        objectMapper);

        service.initializeRootJob(
                conversation(),
                UUID.randomUUID(),
                new ChatTurnRequest("顺便查天气", "fake"),
                new JobInitializationSpec(
                        "node-weather",
                        "顺便查天气",
                        "顺便查天气",
                        "查询最新天气状况",
                        TurnTaskType.WEATHER_QUERY,
                        weatherRecognition()),
                true);

        JsonNode slots = objectMapper.readTree(
                captured.get().clarificationContractJson()).path("slots");
        assertThat(slots).hasSize(1);
        assertThat(slot(slots, "location").path("requiredLevel").asText())
                .isEqualTo("BLOCKING");
        assertThat(slot(slots, "location").path("defaultable").asBoolean())
                .isFalse();
        assertThat(hasSlot(slots, "name")).isFalse();
    }

    /**
     * Finds one slot by key.
     */
    private JsonNode slot(JsonNode slots, String key) {
        for (JsonNode slot : slots) {
            if (key.equals(slot.path("key").asText())) {
                return slot;
            }
        }
        throw new AssertionError("Missing slot: " + key);
    }

    /**
     * Checks whether a slot key exists.
     */
    private boolean hasSlot(JsonNode slots, String key) {
        for (JsonNode slot : slots) {
            if (key.equals(slot.path("key").asText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a low-risk resume recognition with an over-strict model contract.
     */
    private IntentRecognition resumeRecognition() {
        return new IntentRecognition(
                IntentType.CREATE_JOB,
                0.9,
                "TEST",
                "生成个人简历",
                "缺少简历信息",
                List.of(),
                true,
                false,
                IntentRiskLevel.LOW,
                List.of("resume", "weather"),
                "请补充简历信息",
                """
                {
                  "slots": [
                    {"key": "name", "label": "姓名", "required": true, "requiredLevel": "BLOCKING", "defaultable": false, "aliases": ["姓名"]},
                    {"key": "contact", "label": "联系方式", "required": true, "requiredLevel": "BLOCKING", "defaultable": false, "aliases": ["联系方式", "手机", "邮箱"]},
                    {"key": "jobTarget", "label": "求职意向", "required": true, "requiredLevel": "BLOCKING", "defaultable": false, "aliases": ["求职意向", "目标岗位"]},
                    {"key": "education", "label": "教育背景", "required": true, "requiredLevel": "BLOCKING", "defaultable": false, "aliases": ["学历", "学校", "专业"]},
                    {"key": "work_experience", "label": "工作经历", "required": true, "requiredLevel": "BLOCKING", "defaultable": false, "aliases": ["公司", "工作职责"]}
                  ]
                }
                """);
    }

    /**
     * Creates a weather recognition whose model contract missed location.
     */
    private IntentRecognition weatherRecognition() {
        return new IntentRecognition(
                IntentType.CREATE_JOB,
                0.92,
                "TEST",
                "查询最新天气状况",
                "缺少要查询天气的城市或地点",
                List.of(),
                true,
                false,
                IntentRiskLevel.LOW,
                List.of("weather", "needs-fresh-info"),
                "请问你想查哪个城市的天气呢？",
                "{}");
    }

    /**
     * Creates a minimal conversation.
     */
    private ConversationView conversation() {
        return new ConversationView(
                UUID.randomUUID(),
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
     * Creates a waiting Job with one waiting task.
     */
    private JobView waitingJob() {
        UUID jobId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        return new JobView(
                jobId,
                null,
                jobId,
                0,
                "帮我生成一份个人简历",
                "生成个人简历",
                "fake",
                JobStatus.WAITING_HUMAN,
                0,
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                List.of(new TaskView(
                        taskId,
                        "clarification",
                        1,
                        "等待补充任务信息",
                        "生成个人简历",
                        "GENERAL",
                        TaskStatus.WAITING_HUMAN,
                        "LOOP",
                        null,
                        null,
                        null,
                        List.of(),
                        0)),
                List.of());
    }
}
