package com.funjson.metaagent.control.application;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.clarification.application.ClarificationService;
import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.control.api.ChatTurnRequest;
import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.job.api.CreateJobRequest;
import com.funjson.metaagent.job.api.JobView;
import com.funjson.metaagent.job.api.TaskGraphTemplateView;
import com.funjson.metaagent.job.application.DefaultResearchTaskGraphFactory;
import com.funjson.metaagent.job.application.JobService;
import com.funjson.metaagent.job.application.TaskGraphPlanner;
import com.funjson.metaagent.job.application.TaskGraphTemplateService;
import com.funjson.metaagent.job.domain.JobCreationContext;
import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.job.domain.TaskGraphPlanningRequest;
import com.funjson.metaagent.provider.application.ModelCatalogService;
import com.funjson.metaagent.provider.application.ProviderConfigService;
import com.funjson.metaagent.provider.application.port.out.ProviderSecretPort;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.task.api.TaskView;
import com.funjson.metaagent.task.domain.TaskStatus;
import org.springframework.stereotype.Service;

/**
 * Creates the root Job and its TaskGraph for a Control decision.
 *
 * <p>Control decides that a user turn should become a Job. This service owns the
 * implementation details of provider selection, template matching, dynamic
 * planning and TaskGraph-level clarification registration.</p>
 */
@Service
public class ControlJobInitializationService {

    private final TaskGraphPlanner taskGraphPlanner;
    private final TaskGraphTemplateService templateService;
    private final DefaultResearchTaskGraphFactory researchTaskGraphFactory;
    private final JobService jobService;
    private final ClarificationService clarificationService;
    private final ModelCatalogService modelCatalog;
    private final ProviderConfigService providerConfigService;
    private final ProviderSecretPort secretStore;
    private final ObjectMapper objectMapper;

    /**
     * Creates a Job initialization service.
     *
     * @param taskGraphPlanner dynamic TaskGraph planner
     * @param templateService TaskGraphTemplate matcher
     * @param researchTaskGraphFactory default Deep Research TaskGraph factory
     * @param jobService Job application service
     * @param clarificationService clarification application service
     * @param modelCatalog model capability catalog
     * @param providerConfigService provider config service
     * @param secretStore provider secret store
     * @param objectMapper JSON mapper for contract validation
     */
    public ControlJobInitializationService(
            TaskGraphPlanner taskGraphPlanner,
            TaskGraphTemplateService templateService,
            DefaultResearchTaskGraphFactory researchTaskGraphFactory,
            JobService jobService,
            ClarificationService clarificationService,
            ModelCatalogService modelCatalog,
            ProviderConfigService providerConfigService,
            ProviderSecretPort secretStore,
            ObjectMapper objectMapper) {
        this.taskGraphPlanner = taskGraphPlanner;
        this.templateService = templateService;
        this.researchTaskGraphFactory = researchTaskGraphFactory;
        this.jobService = jobService;
        this.clarificationService = clarificationService;
        this.modelCatalog = modelCatalog;
        this.providerConfigService = providerConfigService;
        this.secretStore = secretStore;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a root Job from an intent recognition result.
     *
     * @param conversation current conversation
     * @param userMessageId source user message
     * @param content raw user content
     * @param request chat turn request
     * @param recognition intent recognition result
     * @param modelPlanningAllowed whether model-based TaskGraph planning is allowed
     * @return created Job and TaskGraph snapshot
     */
    public InitializedJob initializeRootJob(
            ConversationView conversation,
            UUID userMessageId,
            String content,
            ChatTurnRequest request,
            IntentRecognition recognition,
            boolean modelPlanningAllowed) {
        String providerId = resolveProvider(
                request.providerId(),
                conversation.defaultProviderId());
        TaskGraphTemplateView matchedTemplate = templateService.match(
                conversation.agentProfileId(),
                recognition.labels()).orElse(null);
        TaskGraphPlan taskGraph = chooseTaskGraph(
                content,
                recognition,
                modelPlanningAllowed,
                matchedTemplate);
        JobView job = jobService.create(
                "chat-job:" + userMessageId,
                new CreateJobRequest(content, providerId),
                JobCreationContext.root(
                        conversation.agentProfileId(),
                        conversation.id(),
                        userMessageId,
                        matchedTemplate == null ? null : matchedTemplate.id(),
                        matchedTemplate == null
                                ? null
                                : matchedTemplate.version()),
                taskGraph);
        registerClarificationIfNeeded(
                conversation.id(),
                job,
                taskGraph);
        return new InitializedJob(job, taskGraph);
    }

    /**
     * Selects the TaskGraph source for the root Job.
     *
     * <p>Configured templates stay authoritative. The built-in Deep Research
     * graph is only used as a framework fallback for explicit deep-research
     * labels; all other cases keep the normal planner behavior.</p>
     */
    private TaskGraphPlan chooseTaskGraph(
            String content,
            IntentRecognition recognition,
            boolean modelPlanningAllowed,
            TaskGraphTemplateView matchedTemplate) {
        if (matchedTemplate != null) {
            return matchedTemplate.graph();
        }
        if (!recognition.requiresClarification()
                && researchTaskGraphFactory.supports(recognition.labels())) {
            return researchTaskGraphFactory.create(
                    content,
                    recognition.goalSummary(),
                    recognition.constraints());
        }
        return taskGraphPlanner.plan(new TaskGraphPlanningRequest(
                content,
                recognition.goalSummary(),
                recognition.constraints(),
                clarificationQuestion(recognition),
                clarificationContractJson(recognition),
                recognition.requiresClarification(),
                recognition.compoundTask(),
                modelPlanningAllowed));
    }

    /**
     * Turns an Intent clarification summary into a direct user question.
     *
     * @param recognition intent recognition result
     * @return user-facing question; empty when clarification is not required
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
     * Reuses the model contract or returns a stable fallback contract.
     *
     * @param recognition intent recognition result
     * @return clarification contract JSON
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
     * Checks whether a candidate contract has a non-empty slots array.
     *
     * @param value candidate contract JSON
     * @return true when the JSON is a valid slot contract
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
     * Resolves the provider used by the Job executor.
     *
     * @param requested request-level provider
     * @param conversationDefault conversation default provider
     * @return executor model ID
     */
    private String resolveProvider(String requested, String conversationDefault) {
        String candidate = requested == null || requested.isBlank()
                ? conversationDefault
                : requested.trim();
        if ("auto".equals(candidate)) {
            return secretStore.configured("deepseek")
                    ? configuredModelOrDefault("deepseek")
                    : modelCatalog.fallbackModelId();
        }
        String modelId = resolveModelId(candidate);
        var model = modelCatalog.require(modelId);
        if (!"fake".equals(model.providerId())
                && !providerConfigService.configured(model.providerId())) {
            throw new RuntimeStateException(
                    "PROVIDER_SECRET_MISSING",
                    model.providerId() + " API key is not configured");
        }
        return modelId;
    }

    /**
     * 把 legacy provider ID 或明确 model ID 解析成 model ID。
     *
     * @param candidate 用户选择值
     * @return 模型 ID
     */
    private String resolveModelId(String candidate) {
        if (modelCatalog.find(candidate).isPresent()) {
            return candidate;
        }
        if ("deepseek".equals(candidate) || "glm".equals(candidate)) {
            return configuredModelOrDefault(candidate);
        }
        throw new IllegalArgumentException("Unsupported executor model: " + candidate);
    }

    /**
     * 优先复用 provider_config 当前模型名，否则返回目录中该 Provider 的第一个模型。
     */
    private String configuredModelOrDefault(String providerId) {
        String configuredModel = providerConfigService.requireProvider(providerId)
                .modelName();
        return modelCatalog.findByProviderModel(providerId, configuredModel)
                .or(() -> modelCatalog.firstByProvider(providerId))
                .map(model -> model.id())
                .orElse(modelCatalog.defaultModelId());
    }

    /**
     * Creates an OPEN clarification request for a WAITING_HUMAN TaskGraph.
     *
     * @param conversationId conversation ID
     * @param job created Job
     * @param taskGraph planned TaskGraph
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
            // The clarification request is the durable reason for
            // WAITING_HUMAN; callers must not infer the reason from Task state.
            clarificationService.openForTaskGraph(
                    conversationId,
                    job.id(),
                    waitingTask.id(),
                    draft);
        });
    }

    /**
     * Returns legal JSON text for optional contract values.
     *
     * @param value possible JSON string
     * @return JSON object text
     */
    private String safeJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    /**
     * Result of creating a Job from a Control decision.
     *
     * @param job created Job
     * @param taskGraph task graph used to create the Job
     */
    public record InitializedJob(
            JobView job,
            TaskGraphPlan taskGraph) {
    }
}
