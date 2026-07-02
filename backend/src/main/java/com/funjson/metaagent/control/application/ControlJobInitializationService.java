package com.funjson.metaagent.control.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.funjson.metaagent.clarification.application.ClarificationService;
import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.control.api.ChatTurnRequest;
import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.intent.domain.TurnTaskType;
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
import com.funjson.metaagent.runtime.domain.TaskIntentScope;
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

    private static final Set<String> SOFTENABLE_GENERATION_SLOTS = Set.of(
            "purpose",
            "usecase",
            "scenario",
            "background",
            "profile",
            "identity",
            "role",
            "occupation",
            "profession",
            "contact",
            "phone",
            "mobile",
            "email",
            "wechat",
            "jobtarget",
            "targetjob",
            "targetposition",
            "desiredposition",
            "position",
            "education",
            "school",
            "major",
            "degree",
            "workexperience",
            "work_experience",
            "projectexperience",
            "project_experience",
            "style",
            "tone",
            "length",
            "wordcount",
            "content",
            "requirements",
            "outputformat",
            "format");

    private static final Set<String> FRESH_OR_EXTERNAL_LABELS = Set.of(
            "needs-web",
            "needs-fresh-info",
            "needs-file-context",
            "needs-rag",
            "weather",
            "tool",
            "external-side-effect",
            "authorization");

    private static final Set<String> SOFTENABLE_TEXT_HINTS = Set.of(
            "purpose",
            "usecase",
            "scenario",
            "background",
            "profile",
            "identity",
            "role",
            "occupation",
            "profession",
            "contact",
            "phone",
            "mobile",
            "email",
            "wechat",
            "jobtarget",
            "targetjob",
            "targetposition",
            "desiredposition",
            "position",
            "联系方式",
            "手机",
            "邮箱",
            "电话",
            "微信",
            "求职意向",
            "应聘职位",
            "目标岗位",
            "想找什么工作",
            "education",
            "school",
            "major",
            "degree",
            "workexperience",
            "work_experience",
            "projectexperience",
            "project_experience",
            "教育背景",
            "学历",
            "学校",
            "专业",
            "毕业时间",
            "工作经历",
            "工作经验",
            "公司",
            "工作时间",
            "工作职责",
            "项目经验",
            "style",
            "tone",
            "length",
            "wordcount",
            "content",
            "requirement",
            "outputformat",
            "format");

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
        return initializeRootJob(
                conversation,
                userMessageId,
                request,
                new JobInitializationSpec(
                        "legacy",
                        content,
                        content,
                        recognition.goalSummary(),
                        TurnTaskType.UNKNOWN,
                        recognition),
                modelPlanningAllowed);
    }

    /**
     * Creates a root Job from a node-scoped initialization spec.
     *
     * @param conversation current conversation
     * @param userMessageId source user message
     * @param request chat turn request
     * @param spec node-scoped Job initialization spec
     * @param modelPlanningAllowed whether model-based TaskGraph planning is allowed
     * @return created Job and TaskGraph snapshot
     */
    public InitializedJob initializeRootJob(
            ConversationView conversation,
            UUID userMessageId,
            ChatTurnRequest request,
            JobInitializationSpec spec,
            boolean modelPlanningAllowed) {
        String content = spec.requestText(request.content());
        IntentRecognition recognition = spec.recognition();
        String providerId = resolveProvider(
                request.providerId(),
                conversation.defaultProviderId());
        TaskGraphTemplateView matchedTemplate = templateService.match(
                conversation.agentProfileId(),
                recognition.labels()).orElse(null);
        TaskGraphPlan taskGraph = chooseTaskGraph(
                content,
                spec,
                modelPlanningAllowed,
                matchedTemplate);
        String clarificationContractJson = clarificationContractJson(spec);
        TaskIntentScope intentScope = taskIntentScope(
                spec,
                clarificationContractJson);
        JobCreationContext creationContext = JobCreationContext.root(
                        conversation.agentProfileId(),
                        conversation.id(),
                        userMessageId,
                        matchedTemplate == null ? null : matchedTemplate.id(),
                        matchedTemplate == null
                                ? null
                                : matchedTemplate.version())
                .withEffectivePolicySnapshotJson(policySnapshotJson(
                        providerId,
                        intentScope));
        JobView job = jobService.create(
                jobIdempotencyKey(
                        userMessageId,
                        spec.nodeId(),
                        content,
                        recognition),
                new CreateJobRequest(content, providerId),
                creationContext,
                taskGraph);
        registerClarificationIfNeeded(
                conversation.id(),
                job,
                taskGraph);
        return new InitializedJob(job, taskGraph);
    }

    /**
     * Builds an idempotency key for one CREATE_JOB action inside a chat turn.
     *
     * <p>A single user message can legally produce multiple CREATE_JOB actions.
     * The key therefore includes a stable action fingerprint instead of only
     * the source message ID; otherwise later actions in the same turn would
     * reuse the first Job and collapse unrelated tasks into one TaskGraph.</p>
     */
    private String jobIdempotencyKey(
            UUID userMessageId,
            String nodeId,
            String content,
            IntentRecognition recognition) {
        String fingerprintSource = "%s\n%s\n%s\n%s".formatted(
                nodeId == null ? "" : nodeId.trim(),
                content == null ? "" : content.trim(),
                recognition.goalSummary(),
                String.join(",", recognition.labels()));
        return "chat-job:%s:%s".formatted(
                userMessageId,
                sha256(fingerprintSource).substring(0, 16));
    }

    /**
     * Computes a stable SHA-256 digest.
     */
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception);
        }
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
            JobInitializationSpec spec,
            boolean modelPlanningAllowed,
            TaskGraphTemplateView matchedTemplate) {
        IntentRecognition recognition = spec.recognition();
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
                clarificationQuestion(spec),
                clarificationContractJson(spec),
                recognition.requiresClarification(),
                recognition.compoundTask(),
                modelPlanningAllowed));
    }

    /**
     * Turns an Intent clarification summary into a direct user question.
     *
     * @param spec node-scoped Job initialization spec
     * @return user-facing question; empty when clarification is not required
     */
    private String clarificationQuestion(JobInitializationSpec spec) {
        IntentRecognition recognition = spec.recognition();
        if (!recognition.requiresClarification()) {
            return "";
        }
        if (!recognition.clarificationQuestion().isBlank()) {
            return recognition.clarificationQuestion();
        }
        if (isWeatherTask(spec)) {
            return "请问你想查询哪个城市或地点的天气？";
        }
        return """
                可以，我需要再确认几件会影响结果的信息：你是谁或你的背景、这份内容用在什么场合、希望正式还是轻松、需要多长，以及有没有必须包含或避免的内容。
                如果你想让我先按通用模板写，也可以直接说“其他随意”或“默认即可”。
                """.trim();
    }

    /**
     * Reuses the model contract or returns a stable fallback contract.
     *
     * @param spec node-scoped Job initialization spec
     * @return clarification contract JSON
     */
    private String clarificationContractJson(JobInitializationSpec spec) {
        IntentRecognition recognition = spec.recognition();
        if (!recognition.requiresClarification()) {
            return "{}";
        }
        if (isWeatherTask(spec)
                && !hasLocationSlot(recognition.clarificationContractJson())) {
            return weatherClarificationContractJson();
        }
        if (hasContractSlots(recognition.clarificationContractJson())) {
            return normalizeLowRiskGenerationContract(
                    recognition,
                    recognition.clarificationContractJson());
        }
        if (isWeatherTask(spec)) {
            return weatherClarificationContractJson();
        }
        return """
                {
                  "version": "v1",
                  "slots": [
                    {"key": "name", "label": "姓名或称呼", "required": true, "requiredLevel": "BLOCKING", "defaultable": false, "aliases": ["name", "姓名", "名字", "称呼"]},
                    {"key": "purpose", "label": "使用场景", "required": true, "requiredLevel": "SOFT", "defaultable": true, "aliases": ["purpose", "useCase", "scenario", "用途", "场景", "场合"]},
                    {"key": "background", "label": "身份或背景", "required": true, "requiredLevel": "SOFT", "defaultable": true, "aliases": ["background", "role", "occupation", "experience", "背景", "身份", "职业", "岗位", "经验"]},
                    {"key": "style", "label": "风格偏好", "required": true, "requiredLevel": "SOFT", "defaultable": true, "aliases": ["style", "tone", "风格", "语气"]},
                    {"key": "length", "label": "长度要求", "required": true, "requiredLevel": "SOFT", "defaultable": true, "aliases": ["length", "wordCount", "长度", "字数", "篇幅"]},
                    {"key": "requirements", "label": "必须包含或避免的内容", "required": false, "requiredLevel": "OPTIONAL", "defaultable": true, "aliases": ["mustInclude", "mustAvoid", "requirements", "特别要求", "避免", "突出"]},
                    {"key": "outputFormat", "label": "输出形式", "required": false, "requiredLevel": "OPTIONAL", "defaultable": true, "aliases": ["outputFormat", "format", "输出形式", "形式"]}
                  ],
                  "defaultConsentPhrases": ["默认即可", "你看着办", "其他随意", "按通用模板", "都行"]
                }
                """.trim();
    }

    /**
     * Returns the stable fallback contract for weather queries.
     *
     * @return weather clarification contract JSON
     */
    private String weatherClarificationContractJson() {
        return """
                {
                  "version": "v1",
                  "slots": [
                    {"key": "location", "label": "城市或地点", "required": true, "requiredLevel": "BLOCKING", "defaultable": false, "aliases": ["location", "city", "城市", "地点", "地区", "哪里", "哪儿"]}
                  ]
                }
                """.trim();
    }

    /**
     * Detects weather tasks from stable task type, labels and goal text.
     *
     * @param spec node-scoped Job initialization spec
     * @return true when this Job should use weather-oriented clarification
     */
    private boolean isWeatherTask(JobInitializationSpec spec) {
        IntentRecognition recognition = spec.recognition();
        String text = normalizeSlotText("%s %s %s %s".formatted(
                spec.taskType(),
                spec.canonicalGoal(),
                spec.originalText(),
                recognition.goalSummary()));
        return spec.taskType() == TurnTaskType.WEATHER_QUERY
                || text.contains("weather")
                || text.contains("天气")
                || text.contains("气温")
                || text.contains("降水");
    }

    /**
     * Checks whether a model-provided contract already contains a location
     * slot. Weather tasks must not fall back to the generation contract.
     *
     * @param value candidate contract JSON
     * @return true when the contract can capture a weather location
     */
    private boolean hasLocationSlot(String value) {
        try {
            JsonNode slots = objectMapper.readTree(safeJson(value))
                    .path("slots");
            if (!slots.isArray()) {
                return false;
            }
            for (JsonNode slot : slots) {
                String text = normalizeSlotText("%s %s %s".formatted(
                        slot.path("key").asText(""),
                        slot.path("label").asText(""),
                        slot.path("aliases").toString()));
                if (text.contains("location")
                        || text.contains("city")
                        || text.contains("城市")
                        || text.contains("地点")
                        || text.contains("地区")) {
                    return true;
                }
            }
            return false;
        } catch (JsonProcessingException exception) {
            return false;
        }
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
     * Softens quality-only slots for low-risk generation tasks.
     *
     * <p>The model still owns the initial contract, but it may over-mark
     * personal-introduction or copywriting preferences as hard blockers. For
     * low-risk, non-tool tasks we normalize known quality slots to SOFT so an
     * explicit “就这些吧/默认即可” can resume execution. Labels that imply fresh
     * data, files, tools, permissions or external side effects skip this
     * normalization entirely.</p>
     */
    private String normalizeLowRiskGenerationContract(
            IntentRecognition recognition,
            String contractJson) {
        if (!canSoftenGenerationContract(recognition)) {
            return contractJson;
        }
        try {
            JsonNode root = objectMapper.readTree(safeJson(contractJson));
            JsonNode slots = root.path("slots");
            if (!root.isObject() || !slots.isArray()) {
                return contractJson;
            }
            for (JsonNode slot : slots) {
                if (slot instanceof ObjectNode objectSlot
                        && isSoftenableGenerationSlot(objectSlot)) {
                    objectSlot.put("requiredLevel", "SOFT");
                    objectSlot.put("defaultable", true);
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            return contractJson;
        }
    }

    /**
     * Checks whether a recognition is safe enough for quality-slot softening.
     */
    private boolean canSoftenGenerationContract(IntentRecognition recognition) {
        boolean hasFreshOrExternalLabel = recognition.labels().stream()
                .map(label -> label.toLowerCase(Locale.ROOT))
                .anyMatch(FRESH_OR_EXTERNAL_LABELS::contains);
        boolean lowRisk = recognition.riskLevel() != null
                && "LOW".equals(recognition.riskLevel().name());
        if (!lowRisk && !looksLikeLowRiskGeneration(recognition)) {
            return false;
        }
        // Mixed-turn model output can occasionally leak labels from a sibling
        // task, for example a resume Job carrying a weather label because the
        // original user turn also asked for weather. The current Job goal is
        // therefore allowed to override contaminated sibling labels when it is
        // clearly a low-risk generation task.
        return !hasFreshOrExternalLabel || looksLikeLowRiskGeneration(recognition);
    }

    /**
     * Checks whether the current Job is a low-risk text generation task.
     */
    private boolean looksLikeLowRiskGeneration(IntentRecognition recognition) {
        String text = normalizeSlotText("%s %s %s".formatted(
                recognition.goalSummary(),
                recognition.decisionSummary(),
                String.join(" ", recognition.labels())));
        return text.contains("简历")
                || text.contains("个人介绍")
                || text.contains("自我介绍")
                || text.contains("介绍")
                || text.contains("文案")
                || text.contains("resume")
                || text.contains("cv")
                || text.contains("profile")
                || text.contains("introduction")
                || text.contains("copywriting")
                || text.contains("生成")
                || text.contains("撰写")
                || text.contains("写");
    }

    /**
     * Checks whether a slot usually describes quality preferences rather than
     * a safety-critical parameter.
     */
    private boolean isSoftenableGenerationSlot(ObjectNode slot) {
        String key = normalizeSlotText(slot.path("key").asText(""));
        String label = normalizeSlotText(slot.path("label").asText(""));
        if (matchesSoftenableGenerationText(key)
                || matchesSoftenableGenerationText(label)) {
            return true;
        }
        JsonNode aliases = slot.path("aliases");
        if (!aliases.isArray()) {
            return false;
        }
        for (JsonNode alias : aliases) {
            if (matchesSoftenableGenerationText(
                    normalizeSlotText(alias.asText("")))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Matches exact stable slot names and compound model-generated names such
     * as scenario_or_purpose or background_description.
     */
    private boolean matchesSoftenableGenerationText(String normalized) {
        if (SOFTENABLE_GENERATION_SLOTS.contains(normalized)) {
            return true;
        }
        return SOFTENABLE_TEXT_HINTS.stream()
                .anyMatch(normalized::contains);
    }

    /**
     * Normalizes slot labels and aliases for conservative semantic matching.
     */
    private String normalizeSlotText(String value) {
        return value == null
                ? ""
                : value.replaceAll("\\s+", "")
                        .trim()
                        .toLowerCase(Locale.ROOT);
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
     * Builds the durable node-scoped task intent snapshot for this Job.
     *
     * @param spec node-scoped initialization spec
     * @param contractJson validated clarification contract JSON
     * @return immutable task intent scope
     */
    private TaskIntentScope taskIntentScope(
            JobInitializationSpec spec,
            String contractJson) {
        IntentRecognition recognition = spec.recognition();
        return new TaskIntentScope(
                spec.nodeId(),
                spec.taskType().name(),
                spec.sourceSpan(),
                spec.originalText(),
                spec.canonicalGoal(),
                recognition.labels(),
                recognition.riskLevel() == null
                        ? ""
                        : recognition.riskLevel().name(),
                contractJson,
                allowedToolIds(spec.taskType()),
                true);
    }

    /**
     * Computes the framework tool allowlist for a stable task type.
     *
     * @param taskType stable task category
     * @return tool IDs allowed for this task
     */
    private List<String> allowedToolIds(TurnTaskType taskType) {
        return switch (taskType) {
            case WEATHER_QUERY -> List.of("weather.current");
            case WEB_SEARCH -> List.of("web.search", "web.fetch", "web.extract");
            case DEEP_RESEARCH -> List.of(
                    "web.search",
                    "web.fetch",
                    "web.extract",
                    "file.read",
                    "file.search");
            case FILE_QA -> List.of("file.list", "file.read", "file.search");
            case FILE_OPERATION -> List.of(
                    "file.list",
                    "file.read",
                    "file.search",
                    "file.write");
            case TOOL_ACTION -> List.of("skill.search", "skill.load");
            default -> List.of();
        };
    }

    /**
     * Serializes the effective policy snapshot persisted on the Job.
     *
     * @param providerId selected executor model
     * @param intentScope node-scoped task intent snapshot
     * @return JSON string stored in job.effective_policy_snapshot
     */
    private String policySnapshotJson(
            String providerId,
            TaskIntentScope intentScope) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "version",
                    "v1",
                    "providerId",
                    providerId,
                    "intentScope",
                    intentScope));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize Job policy snapshot",
                    exception);
        }
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
