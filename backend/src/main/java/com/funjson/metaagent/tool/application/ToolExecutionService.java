package com.funjson.metaagent.tool.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.capability.application.CapabilityApplicationService;
import com.funjson.metaagent.clarification.application.ClarificationService;
import com.funjson.metaagent.clarification.domain.ClarificationReasonType;
import com.funjson.metaagent.clarification.domain.ClarificationRequestDraft;
import com.funjson.metaagent.clarification.domain.ClarificationSourceType;
import com.funjson.metaagent.file.api.FileContentView;
import com.funjson.metaagent.file.application.FileAttachmentService;
import com.funjson.metaagent.loop.domain.LoopActionResult;
import com.funjson.metaagent.loop.domain.LoopActionType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.tool.api.ToolInvocationView;
import com.funjson.metaagent.tool.application.port.out.ToolStore;
import com.funjson.metaagent.tool.domain.ScriptToolSpec;
import com.funjson.metaagent.tool.domain.ToolResult;
import com.funjson.metaagent.tool.domain.ToolType;
import com.funjson.metaagent.websearch.application.WebSearchService;
import com.funjson.metaagent.websearch.application.port.out.WebResearchStore;
import com.funjson.metaagent.websearch.domain.WebEvidenceExtraction;
import com.funjson.metaagent.websearch.domain.WebEvidenceItem;
import com.funjson.metaagent.websearch.domain.WebResearchContext;
import com.funjson.metaagent.websearch.domain.WebSearchQuery;
import com.funjson.metaagent.websearch.domain.WebSearchResult;
import com.funjson.metaagent.websearch.domain.WebSourceDocument;
import com.funjson.metaagent.weather.application.WeatherService;
import com.funjson.metaagent.weather.domain.WeatherDailyForecast;
import com.funjson.metaagent.weather.domain.WeatherForecast;
import com.funjson.metaagent.weather.domain.WeatherQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 执行框架 Tool 和 SkillPackage 注册的脚本 Tool。
 */
@Service
public class ToolExecutionService {

    private static final Duration SCRIPT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_OUTPUT_LENGTH = 32_000;
    private static final Set<String> ALLOWED_INTERPRETERS =
            Set.of("python", "python3", "node", "internal.echo");
    private static final Set<String> ALLOWED_SIDE_EFFECTS =
            Set.of("NONE", "READ_ONLY");

    private final ToolStore toolStore;
    private final CapabilityApplicationService capabilityApplicationService;
    private final ClarificationService clarificationService;
    private final FileAttachmentService fileAttachmentService;
    private final WebSearchService webSearchService;
    private final WebResearchStore webResearchStore;
    private final WeatherService weatherService;
    private final ObjectMapper objectMapper;

    /**
     * 创建 Tool 执行服务。
     *
     * @param toolStore Tool Store
     * @param capabilityApplicationService Capability 应用服务
     * @param clarificationService 澄清服务
     * @param fileAttachmentService 文件附件服务
     * @param webSearchService Web Search 服务
     * @param webResearchStore Web Research 证据池
     * @param objectMapper JSON Mapper
     */
    public ToolExecutionService(
            ToolStore toolStore,
            CapabilityApplicationService capabilityApplicationService,
            ClarificationService clarificationService,
            FileAttachmentService fileAttachmentService,
            WebSearchService webSearchService,
            WebResearchStore webResearchStore,
            WeatherService weatherService,
            ObjectMapper objectMapper) {
        this.toolStore = toolStore;
        this.capabilityApplicationService = capabilityApplicationService;
        this.clarificationService = clarificationService;
        this.fileAttachmentService = fileAttachmentService;
        this.webSearchService = webSearchService;
        this.webResearchStore = webResearchStore;
        this.weatherService = weatherService;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行无 Loop 上下文的 Tool 调用，主要用于 API 和验收测试。
     *
     * @param command 调用命令
     * @return 调用视图
     */
    public ToolInvocationView invoke(ToolInvocationCommand command) {
        ToolResult result = invokeInternal(command, null);
        return new ToolInvocationView(
                result.invocationId(),
                command.toolId(),
                result.success() ? "COMPLETED" : "FAILED",
                result.summary(),
                result.attributes());
    }

    /**
     * 执行带 Loop 上下文的 Tool 调用，并转换为 Loop Observation。
     *
     * @param context LoopNode 上下文
     * @param command 调用命令
     * @return Loop Action Result
     */
    public LoopActionResult invokeForLoop(
            RunExecutionContext context,
            ToolInvocationCommand command) {
        return invokeForLoop(
                context,
                command,
                LoopActionType.TOOL_CALL);
    }

    /**
     * 执行带 Loop 上下文的 Tool 调用，并保留规划动作语义。
     *
     * @param context LoopNode 上下文
     * @param command 调用命令
     * @param actionType Planning 阶段选择的动作类型
     * @return Loop Action Result
     */
    public LoopActionResult invokeForLoop(
            RunExecutionContext context,
            ToolInvocationCommand command,
            LoopActionType actionType) {
        return LoopActionResult.fromTool(
                invokeInternal(command, context, true),
                actionType);
    }

    /**
     * 执行 Tool 并维护审计状态。
     */
    @Transactional
    protected ToolResult invokeInternal(
            ToolInvocationCommand command,
            RunExecutionContext context) {
        return invokeInternal(command, context, false);
    }

    /**
     * 执行 Tool 并维护审计状态。
     *
     * <p>Loop 内部调用和直接 API 调用的失败语义不同：Loop 需要把可恢复工具失败
     * 作为 Observation 交给模型纠偏；直接 API 调用仍保留异常，方便开发和验收发现
     * 配置问题。该边界避免外部系统异常绕过模型，直接进入用户可见聊天消息。</p>
     *
     * @param command Tool 调用命令
     * @param context 可选 Loop 上下文
     * @param returnFailureObservation 是否把可恢复失败返回为 ToolResult
     * @return ToolResult
     */
    @Transactional
    protected ToolResult invokeInternal(
            ToolInvocationCommand command,
            RunExecutionContext context,
            boolean returnFailureObservation) {
        var existing = toolStore.findInvocationByIdempotencyKey(
                command.idempotencyKey());
        if (existing.isPresent()) {
            return fromExisting(existing.get());
        }
        UUID invocationId = UUID.randomUUID();
        String argumentsJson = json(command.arguments());
        String toolType = resolveToolType(command.toolId()).name();
        toolStore.insertInvocation(
                invocationId,
                command.toolId(),
                toolType,
                command.idempotencyKey(),
                argumentsJson,
                command.jobId(),
                command.taskId(),
                command.taskRunId(),
                command.loopRunId(),
                command.loopNodeId());
        toolStore.markRunning(invocationId);
        try {
            ToolResult result = withToolMetadata(
                    execute(
                            invocationId,
                            command,
                            context),
                    command.toolId());
            toolStore.complete(
                    invocationId,
                    json(result.attributes()));
            return result;
        } catch (RuntimeException failure) {
            ToolResult result = failedToolResult(
                    invocationId,
                    command.toolId(),
                    toolType,
                    failure);
            toolStore.fail(
                    invocationId,
                    result.summary(),
                    json(result.attributes()));
            if (returnFailureObservation
                    && canReturnFailureObservation(command, context)) {
                // 工具失败已经写入审计表；Loop 继续以 Observation 形式推进，
                // 后续由模型包装为用户可见说明或选择新的纠偏动作。
                return result;
            }
            throw failure;
        }
    }

    /**
     * 判断工具失败是否可以作为 Loop Observation 返回。
     *
     * <p>clarification.request 是控制协议工具，失败通常意味着数据库或状态机异常，
     * 模型无法自行修复，所以仍作为致命错误抛出。</p>
     *
     * @param command Tool 调用命令
     * @param context Loop 上下文
     * @return true 表示可以交给 Loop 纠偏
     */
    private boolean canReturnFailureObservation(
            ToolInvocationCommand command,
            RunExecutionContext context) {
        return context != null
                && !"clarification.request".equals(command.toolId());
    }

    /**
     * 构造模型可消费、但不应直接展示给用户的失败 Observation。
     *
     * @param invocationId ToolInvocation ID
     * @param toolId Tool ID
     * @param toolType Tool 类型
     * @param failure 原始异常
     * @return 失败 ToolResult
     */
    private ToolResult failedToolResult(
            UUID invocationId,
            String toolId,
            String toolType,
            RuntimeException failure) {
        String errorType = failure.getClass().getSimpleName();
        String message = failure.getMessage() == null
                ? "tool failed"
                : failure.getMessage();
        Map<String, Object> failureAttributes = new LinkedHashMap<>();
        failureAttributes.put("toolId", toolId);
        failureAttributes.put("toolType", toolType);
        failureAttributes.put("success", false);
        failureAttributes.put("errorType", errorType);
        failureAttributes.put("message", message);
        String content = """
                工具调用失败，当前结果只能作为内部 Observation 使用。
                toolId: %s
                errorType: %s
                errorMessage: %s

                请不要把异常原文直接展示给用户；请基于上下文选择重试、换来源、
                降级回答，或用自然语言说明当前无法完成的部分。
                """.formatted(toolId, errorType, message).trim();
        return new ToolResult(
                invocationId,
                false,
                "Tool call failed and was captured as Observation",
                content,
                failureAttributes);
    }

    /**
     * 根据 Tool ID 分派内部工具或脚本工具。
     */
    private ToolResult execute(
            UUID invocationId,
            ToolInvocationCommand command,
            RunExecutionContext context) {
        return switch (command.toolId()) {
            case "skill.search" -> skillSearch(invocationId, command);
            case "skill.load" -> skillLoad(invocationId, command, context);
            case "clarification.request" ->
                    clarificationRequest(invocationId, command, context);
            case "web.search" -> webSearch(invocationId, command);
            case "web.fetch" -> webFetch(invocationId, command);
            case "web.extract" -> webExtract(invocationId, command);
            case "weather.current" -> weatherCurrent(invocationId, command);
            case "file.list" -> fileList(invocationId, context);
            case "file.read" -> fileRead(invocationId, command, context);
            case "file.search" -> fileSearch(invocationId, command, context);
            case "file.write" -> fileWrite(invocationId, command, context);
            default -> executeScriptTool(invocationId, command);
        };
    }

    /**
     * 返回当前可用脚本工具摘要。
     */
    private ToolResult skillSearch(
            UUID invocationId,
            ToolInvocationCommand command) {
        String query = String.valueOf(
                command.arguments().getOrDefault("query", ""));
        var tools = toolStore.findScriptToolDefinitions().stream()
                .filter(tool -> query.isBlank()
                        || tool.name().contains(query)
                        || tool.description().contains(query))
                .map(tool -> Map.of(
                        "name", tool.name(),
                        "type", tool.type().name(),
                        "description", tool.description()))
                .toList();
        return new ToolResult(
                invocationId,
                true,
                "Skill search returned " + tools.size() + " tools",
                json(tools),
                Map.of("tools", tools));
    }

    /**
     * 把 Skill Capability 加载到当前 LoopNode。
     */
    private ToolResult skillLoad(
            UUID invocationId,
            ToolInvocationCommand command,
            RunExecutionContext context) {
        if (context == null) {
            throw new RuntimeStateException(
                    "TOOL_CONTEXT_REQUIRED",
                    "skill.load requires Loop context");
        }
        String skillId = required(command.arguments(), "skillId");
        int version = Integer.parseInt(
                String.valueOf(command.arguments()
                        .getOrDefault("version", "1")));
        capabilityApplicationService.apply(
                context,
                new CapabilityRef(skillId, version));
        return new ToolResult(
                invocationId,
                true,
                "Skill loaded: " + skillId + "@" + version,
                "Skill loaded",
                Map.of(
                        "skillId", skillId,
                        "version", version));
    }

    /**
     * 执行 SkillPackage 注册的脚本工具。
     */
    /**
     * 创建结构化澄清请求，并返回给 Loop Kernel 做挂起。
     *
     * @param invocationId ToolInvocation ID
     * @param command Tool 调用命令
     * @param context LoopNode 上下文
     * @return Tool 结果，包含 clarificationRequestId
     */
    private ToolResult clarificationRequest(
            UUID invocationId,
            ToolInvocationCommand command,
            RunExecutionContext context) {
        if (context == null) {
            throw new RuntimeStateException(
                    "TOOL_CONTEXT_REQUIRED",
                    "clarification.request requires Loop context");
        }
        UUID conversationId = toolStore.findConversationIdByJobId(
                        context.jobId())
                .orElseThrow(() -> new RuntimeStateException(
                        "CONVERSATION_NOT_FOUND",
                        "Job conversation is unavailable: "
                                + context.jobId()));
        String question = required(command.arguments(), "question");
        String blockingSummary = String.valueOf(command.arguments()
                .getOrDefault("blockingSummary", question));
        String contractJson = clarificationContractJson(command.arguments());
        ClarificationReasonType reasonType = reasonType(command.arguments());
        UUID requestId = clarificationService.open(
                conversationId,
                context.jobId(),
                context.taskId(),
                context.taskRunId(),
                context.loopNodeId(),
                new ClarificationRequestDraft(
                        ClarificationSourceType.LOOP_NODE,
                        reasonType,
                        blockingSummary,
                        question,
                        1,
                        contractJson));
        toolStore.attachClarification(invocationId, requestId);
        return new ToolResult(
                invocationId,
                true,
                "Clarification request opened",
                question,
                Map.of(
                        "clarificationRequestId", requestId,
                        "question", question,
                        "contractJson", contractJson,
                        "reasonType", reasonType.name()));
    }

    /**
     * 执行网络搜索。
     *
     * @param invocationId ToolInvocation ID
     * @param command Tool 命令
     * @return 搜索结果
     */
    private ToolResult webSearch(
            UUID invocationId,
            ToolInvocationCommand command) {
        String query = required(command.arguments(), "query");
        int limit = intArgument(command.arguments(), "limit", 5);
        Integer recencyDays = optionalInt(command.arguments(), "recencyDays");
        List<String> domains = stringList(command.arguments(), "domains");
        String locale = String.valueOf(
                command.arguments().getOrDefault("locale", ""));
        WebSearchQuery structuredQuery = new WebSearchQuery(
                query,
                limit,
                recencyDays,
                domains,
                locale);
        var results = webSearchService.search(structuredQuery);
        UUID searchRunId = recordSearchRun(
                invocationId,
                command,
                structuredQuery,
                results);
        String content = results.isEmpty()
                ? "没有返回搜索结果。"
                : results.stream()
                        .map(result -> "- [%d] %s\n  url: %s\n  sourceType: %s\n  snippet: %s%s"
                                .formatted(
                                        result.rank(),
                                        result.title(),
                                        result.url(),
                                        result.sourceType(),
                                        result.snippet(),
                                        result.publishedAt() == null
                                                ? ""
                                                : "\n  publishedAt: "
                                                        + result.publishedAt()))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("");
        return new ToolResult(
                invocationId,
                true,
                "Web search returned " + results.size() + " results",
                content,
                Map.of(
                        "searchRunId", searchRunId,
                        "query", query,
                        "recencyDays", recencyDays == null ? "" : recencyDays,
                        "domains", domains,
                        "results", results.stream()
                                .map(this::webSearchResultAttributes)
                                .toList(),
                        "stdout", content));
    }

    /**
     * 打开公开网页并返回清洗后的正文片段。
     *
     * @param invocationId ToolInvocation ID
     * @param command Tool 命令
     * @return 来源文档
     */
    private ToolResult webFetch(
            UUID invocationId,
            ToolInvocationCommand command) {
        String url = required(command.arguments(), "url");
        int maxChars = intArgument(command.arguments(), "maxChars", 8_000);
        WebSourceDocument document = webSearchService.fetch(url, maxChars);
        UUID sourceDocumentId = recordSourceDocument(
                invocationId,
                command,
                document);
        String content = """
                title: %s
                url: %s
                sourceType: %s
                fetchedAt: %s

                %s
                """.formatted(
                document.title(),
                document.url(),
                document.sourceType(),
                document.fetchedAt(),
                document.text()).trim();
        return new ToolResult(
                invocationId,
                true,
                "Fetched and extracted web document",
                content,
                Map.of(
                        "sourceDocumentId", sourceDocumentId,
                        "document", webSourceDocumentAttributes(document),
                        "stdout", content));
    }

    /**
     * 从公开网页抽取与查询相关的证据片段。
     *
     * @param invocationId ToolInvocation ID
     * @param command Tool 命令
     * @return 证据列表
     */
    private ToolResult webExtract(
            UUID invocationId,
            ToolInvocationCommand command) {
        String url = required(command.arguments(), "url");
        String query = String.valueOf(
                command.arguments().getOrDefault("query", ""));
        int maxEvidence = intArgument(
                command.arguments(),
                "maxEvidence",
                5);
        WebEvidenceExtraction extraction = webSearchService.extract(
                url,
                query,
                maxEvidence);
        UUID sourceDocumentId = recordSourceDocument(
                invocationId,
                command,
                extraction.document());
        List<WebEvidenceItem> evidence = extraction.evidence();
        recordEvidenceItems(
                sourceDocumentId,
                invocationId,
                command,
                evidence);
        String content = evidence.isEmpty()
                ? "没有抽取到匹配证据。"
                : evidence.stream()
                        .map(item -> "- %s\n  url: %s\n  sourceType: %s\n  relevance: %.2f"
                                .formatted(
                                        item.excerpt(),
                                        item.sourceUrl(),
                                        item.sourceType(),
                                        item.relevanceScore()))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("");
        return new ToolResult(
                invocationId,
                true,
                "Extracted " + evidence.size() + " web evidence items",
                content,
                Map.of(
                        "sourceDocumentId", sourceDocumentId,
                        "url", url,
                        "query", query,
                        "evidence", evidence.stream()
                                .map(this::webEvidenceAttributes)
                                .toList(),
                        "stdout", content));
    }

    /**
     * 查询当前天气和短期预报。
     *
     * @param invocationId ToolInvocation ID
     * @param command Tool 调用命令
     * @return 天气 Observation
     */
    private ToolResult weatherCurrent(
            UUID invocationId,
            ToolInvocationCommand command) {
        WeatherQuery query = new WeatherQuery(
                required(command.arguments(), "location"),
                intArgument(command.arguments(), "forecastDays", 3),
                String.valueOf(command.arguments()
                        .getOrDefault("locale", "zh-CN")));
        WeatherForecast forecast = weatherService.forecast(query);
        String content = renderWeather(forecast);
        return new ToolResult(
                invocationId,
                true,
                "Weather returned current conditions for "
                        + forecast.location().name(),
                content,
                Map.of(
                        "location", weatherLocationAttributes(forecast),
                        "current", weatherCurrentAttributes(forecast),
                        "daily", forecast.daily().stream()
                                .map(this::weatherDailyAttributes)
                                .toList(),
                        "fetchedAt", forecast.fetchedAt().toString(),
                        "timezone", forecast.timezone(),
                        "stdout", content));
    }

    /**
     * 列出当前 Conversation 文件。
     *
     * @param invocationId ToolInvocation ID
     * @param context LoopNode 上下文
     * @return 文件列表结果
     */
    private ToolResult fileList(
            UUID invocationId,
            RunExecutionContext context) {
        UUID conversationId = requireConversation(context);
        var files = fileAttachmentService.list(conversationId);
        String content = files.isEmpty()
                ? "当前 Conversation 没有上传文件。"
                : files.stream()
                        .map(file -> "- id=%s name=%s type=%s size=%d"
                                .formatted(
                                        file.id(),
                                        file.fileName(),
                                        file.contentType(),
                                        file.sizeBytes()))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("");
        return new ToolResult(
                invocationId,
                true,
                "File list returned " + files.size() + " files",
                content,
                Map.of(
                        "files", files,
                        "stdout", content));
    }

    /**
     * 读取当前 Conversation 文本文件。
     *
     * @param invocationId ToolInvocation ID
     * @param command Tool 命令
     * @param context LoopNode 上下文
     * @return 文件正文结果
     */
    private ToolResult fileRead(
            UUID invocationId,
            ToolInvocationCommand command,
            RunExecutionContext context) {
        UUID conversationId = requireConversation(context);
        int maxChars = intArgument(command.arguments(), "maxChars", 30_000);
        FileContentView content;
        Object fileId = command.arguments().get("fileId");
        if (fileId != null && !String.valueOf(fileId).isBlank()) {
            content = fileAttachmentService.read(
                    conversationId,
                    UUID.fromString(String.valueOf(fileId)),
                    maxChars);
        } else {
            content = fileAttachmentService.readByName(
                    conversationId,
                    required(command.arguments(), "fileName"),
                    maxChars);
        }
        String rendered = """
                文件：%s
                fileId：%s
                truncated：%s

                %s
                """.formatted(
                content.fileName(),
                content.id(),
                content.truncated(),
                content.content()).trim();
        return new ToolResult(
                invocationId,
                true,
                "File read completed: " + content.fileName(),
                rendered,
                Map.of(
                        "fileId", content.id(),
                        "fileName", content.fileName(),
                        "contentType", content.contentType(),
                        "truncated", content.truncated(),
                        "stdout", rendered));
    }

    /**
     * 搜索当前 Conversation 文件。
     *
     * @param invocationId ToolInvocation ID
     * @param command Tool 命令
     * @param context LoopNode 上下文
     * @return 搜索结果
     */
    private ToolResult fileSearch(
            UUID invocationId,
            ToolInvocationCommand command,
            RunExecutionContext context) {
        UUID conversationId = requireConversation(context);
        String query = String.valueOf(
                command.arguments().getOrDefault("query", ""));
        int maxMatches = intArgument(
                command.arguments(),
                "maxMatches",
                10);
        var matches = fileAttachmentService.search(
                conversationId,
                query,
                maxMatches);
        String content = matches.isEmpty()
                ? "没有找到匹配的文件片段。"
                : matches.stream()
                        .map(match -> "- file=%s id=%s snippet=%s"
                                .formatted(
                                        match.fileName(),
                                        match.fileId(),
                                        match.snippet()
                                                .replaceAll("\\s+", " ")
                                                .trim()))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("");
        return new ToolResult(
                invocationId,
                true,
                "File search returned " + matches.size() + " matches",
                content,
                Map.of(
                        "query", query,
                        "matches", matches,
                        "stdout", content));
    }

    /**
     * 写入新的受控 Conversation 文本文件。
     *
     * @param invocationId ToolInvocation ID
     * @param command Tool 命令
     * @param context LoopNode 上下文
     * @return 写入结果
     */
    private ToolResult fileWrite(
            UUID invocationId,
            ToolInvocationCommand command,
            RunExecutionContext context) {
        UUID conversationId = requireConversation(context);
        var file = fileAttachmentService.writeText(
                conversationId,
                required(command.arguments(), "fileName"),
                required(command.arguments(), "content"));
        String content = "已写入文件：%s（id=%s，size=%d）".formatted(
                file.fileName(),
                file.id(),
                file.sizeBytes());
        return new ToolResult(
                invocationId,
                true,
                "File write completed: " + file.fileName(),
                content,
                Map.of(
                        "fileId", file.id(),
                        "fileName", file.fileName(),
                        "sizeBytes", file.sizeBytes(),
                        "stdout", content));
    }

    /**
     * 执行 SkillPackage 注册的脚本 Tool。
     *
     * @param invocationId ToolInvocation ID
     * @param command Tool 调用命令
     * @return Tool 执行结果
     */
    private ToolResult executeScriptTool(
            UUID invocationId,
            ToolInvocationCommand command) {
        ScriptToolSpec spec = toolStore.findScriptTool(command.toolId())
                .orElseThrow(() -> new RuntimeStateException(
                        "TOOL_NOT_FOUND",
                        "Tool not found: " + command.toolId()));
        requireExecutable(spec);
        requireArguments(spec, command.arguments());
        if ("internal.echo".equals(spec.interpreter())) {
            return new ToolResult(
                    invocationId,
                    true,
                    "Internal echo tool completed",
                    json(command.arguments()),
                    Map.of(
                            "stdout", json(command.arguments()),
                            "sideEffectClass", spec.sideEffectClass()));
        }
        ProcessResult processResult = runProcess(spec, command.arguments());
        if (processResult.exitCode() != 0) {
            throw new RuntimeStateException(
                    "TOOL_PROCESS_FAILED",
                    "Tool process exited with code "
                            + processResult.exitCode());
        }
        return new ToolResult(
                invocationId,
                true,
                "Script tool completed",
                processResult.stdout(),
                Map.of(
                        "stdout", processResult.stdout(),
                        "stderr", processResult.stderr(),
                        "exitCode", processResult.exitCode(),
                        "sideEffectClass", spec.sideEffectClass()));
    }

    /**
     * 校验解释器和副作用分类。
     */
    private void requireExecutable(ScriptToolSpec spec) {
        if (!ALLOWED_INTERPRETERS.contains(spec.interpreter())) {
            throw new RuntimeStateException(
                    "TOOL_INTERPRETER_NOT_ALLOWED",
                    "Interpreter is not allowed: " + spec.interpreter());
        }
        if (!ALLOWED_SIDE_EFFECTS.contains(spec.sideEffectClass())) {
            throw new RuntimeStateException(
                    "TOOL_SIDE_EFFECT_NOT_ALLOWED",
                    "Tool side effect requires approval: "
                            + spec.sideEffectClass());
        }
    }

    /**
     * 根据 JSON Schema required 字段做最小参数校验。
     */
    private void requireArguments(
            ScriptToolSpec spec,
            Map<String, Object> arguments) {
        try {
            Map<String, Object> schema = objectMapper.readValue(
                    spec.argumentSchemaJson(),
                    new TypeReference<>() {
                    });
            Object required = schema.get("required");
            if (required instanceof List<?> names) {
                for (Object name : names) {
                    String key = String.valueOf(name);
                    if (!arguments.containsKey(key)
                            || arguments.get(key) == null
                            || String.valueOf(arguments.get(key)).isBlank()) {
                        throw new RuntimeStateException(
                                "TOOL_ARGUMENT_MISSING",
                                "Tool argument is required: " + key);
                    }
                }
            }
        } catch (JsonProcessingException exception) {
            throw new RuntimeStateException(
                    "TOOL_SCHEMA_INVALID",
                    "Tool argument schema cannot be parsed");
        }
    }

    /**
     * 在隔离临时目录中执行脚本工具。
     */
    private ProcessResult runProcess(
            ScriptToolSpec spec,
            Map<String, Object> arguments) {
        try {
            Path workspace = Files.createTempDirectory(
                    "meta-agent-tool-");
            Path script = workspace.resolve(
                    Path.of(spec.resourcePath()).getFileName());
            Path argsFile = workspace.resolve("arguments.json");
            Files.writeString(
                    script,
                    spec.scriptContent(),
                    StandardCharsets.UTF_8);
            Files.writeString(
                    argsFile,
                    json(arguments),
                    StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(spec.interpreter());
            command.add(script.toString());
            command.add(argsFile.toString());
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workspace.toFile());
            builder.environment().clear();
            builder.environment().put(
                    "META_AGENT_TOOL_ARGUMENTS",
                    argsFile.toString());
            Process process = builder.start();
            boolean completed = process.waitFor(
                    SCRIPT_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new RuntimeStateException(
                        "TOOL_TIMEOUT",
                        "Tool timed out after " + SCRIPT_TIMEOUT);
            }
            return new ProcessResult(
                    process.exitValue(),
                    abbreviate(new String(
                            process.getInputStream().readAllBytes(),
                            StandardCharsets.UTF_8)),
                    abbreviate(new String(
                            process.getErrorStream().readAllBytes(),
                            StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new RuntimeStateException(
                    "TOOL_PROCESS_UNAVAILABLE",
                    "Tool process cannot be started: "
                            + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeStateException(
                    "TOOL_INTERRUPTED",
                    "Tool execution was interrupted");
        }
    }

    /**
     * 判断工具类型。
     */
    private ToolType resolveToolType(String toolId) {
        return switch (toolId) {
            case "skill.search" -> ToolType.SKILL_SEARCH;
            case "skill.load" -> ToolType.SKILL_LOAD;
            case "clarification.request" -> ToolType.CLARIFICATION;
            case "web.search", "web.fetch", "web.extract",
                    "weather.current" ->
                    ToolType.RETRIEVAL;
            case "file.list", "file.read", "file.search" ->
                    ToolType.RETRIEVAL;
            case "file.write" -> ToolType.FUNCTION;
            default -> ToolType.FUNCTION;
        };
    }

    /**
     * 给 Tool 结果补充统一元数据。
     *
     * @param result 原始 Tool 结果
     * @param toolId Tool ID
     * @return 可用于纠偏、Agent Path 和审计重放的 Tool 结果
     */
    private ToolResult withToolMetadata(
            ToolResult result,
            String toolId) {
        Map<String, Object> attributes = new LinkedHashMap<>(
                result.attributes());
        attributes.putIfAbsent("toolId", toolId);
        attributes.putIfAbsent("toolType", resolveToolType(toolId).name());
        attributes.putIfAbsent("success", result.success());
        return new ToolResult(
                result.invocationId(),
                result.success(),
                result.summary(),
                result.content(),
                attributes);
    }

    /**
     * Converts a search result into JSON-safe primitive attributes.
     */
    private Map<String, Object> webSearchResultAttributes(
            WebSearchResult result) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("title", result.title());
        attributes.put("url", result.url());
        attributes.put("snippet", result.snippet());
        attributes.put(
                "publishedAt",
                result.publishedAt() == null
                        ? ""
                        : result.publishedAt().toString());
        attributes.put("provider", result.provider());
        attributes.put("rank", result.rank());
        attributes.put("sourceType", result.sourceType().name());
        return attributes;
    }

    /**
     * Converts a fetched web document into bounded JSON-safe attributes.
     */
    private Map<String, Object> webSourceDocumentAttributes(
            WebSourceDocument document) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("url", document.url());
        attributes.put("title", document.title());
        attributes.put("description", document.description());
        attributes.put("sourceType", document.sourceType().name());
        attributes.put("contentType", document.contentType());
        attributes.put("contentHash", document.contentHash());
        attributes.put("fetchedAt", document.fetchedAt().toString());
        attributes.put("textExcerpt", abbreviate(document.text()));
        return attributes;
    }

    /**
     * Converts an evidence item into JSON-safe primitive attributes.
     */
    private Map<String, Object> webEvidenceAttributes(
            WebEvidenceItem evidence) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("sourceUrl", evidence.sourceUrl());
        attributes.put("title", evidence.title());
        attributes.put("excerpt", evidence.excerpt());
        attributes.put("relevanceScore", evidence.relevanceScore());
        attributes.put("sourceType", evidence.sourceType().name());
        return attributes;
    }

    /**
     * 持久化一次搜索运行及候选来源。
     *
     * @param invocationId ToolInvocation ID
     * @param command Tool 调用命令
     * @param query 结构化搜索查询
     * @param results 搜索候选结果
     * @return SearchRun ID
     */
    /**
     * Renders weather data as a compact model-facing observation.
     */
    private String renderWeather(WeatherForecast forecast) {
        String daily = forecast.daily().stream()
                .map(day -> "- %s：%s，%.1f℃~%.1f℃，降水概率 %d%%"
                        .formatted(
                                day.date(),
                                day.condition(),
                                day.minTemperatureCelsius(),
                                day.maxTemperatureCelsius(),
                                day.precipitationProbabilityMaxPercent()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- 暂无预报");
        return """
                地点：%s%s%s
                查询时间：%s（%s）
                当前：%s，%.1f℃，体感 %.1f℃，湿度 %d%%，降水 %.1fmm，风速 %.1fkm/h

                短期预报：
                %s
                """.formatted(
                forecast.location().name(),
                forecast.location().admin1().isBlank()
                        ? ""
                        : "，" + forecast.location().admin1(),
                forecast.location().country().isBlank()
                        ? ""
                        : "，" + forecast.location().country(),
                forecast.fetchedAt(),
                forecast.timezone(),
                forecast.current().condition(),
                forecast.current().temperatureCelsius(),
                forecast.current().apparentTemperatureCelsius(),
                forecast.current().relativeHumidityPercent(),
                forecast.current().precipitationMm(),
                forecast.current().windSpeedKmh(),
                daily).trim();
    }

    /**
     * Converts weather location into JSON-safe primitive attributes.
     */
    private Map<String, Object> weatherLocationAttributes(
            WeatherForecast forecast) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", forecast.location().name());
        attributes.put("country", forecast.location().country());
        attributes.put("admin1", forecast.location().admin1());
        attributes.put("latitude", forecast.location().latitude());
        attributes.put("longitude", forecast.location().longitude());
        attributes.put("timezone", forecast.location().timezone());
        return attributes;
    }

    /**
     * Converts current weather into JSON-safe primitive attributes.
     */
    private Map<String, Object> weatherCurrentAttributes(
            WeatherForecast forecast) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("time", forecast.current().time());
        attributes.put("condition", forecast.current().condition());
        attributes.put(
                "temperatureCelsius",
                forecast.current().temperatureCelsius());
        attributes.put(
                "apparentTemperatureCelsius",
                forecast.current().apparentTemperatureCelsius());
        attributes.put(
                "relativeHumidityPercent",
                forecast.current().relativeHumidityPercent());
        attributes.put("precipitationMm", forecast.current().precipitationMm());
        attributes.put("windSpeedKmh", forecast.current().windSpeedKmh());
        attributes.put(
                "windDirectionDegrees",
                forecast.current().windDirectionDegrees());
        attributes.put("weatherCode", forecast.current().weatherCode());
        return attributes;
    }

    /**
     * Converts daily weather into JSON-safe primitive attributes.
     */
    private Map<String, Object> weatherDailyAttributes(
            WeatherDailyForecast day) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("date", day.date().toString());
        attributes.put("condition", day.condition());
        attributes.put(
                "maxTemperatureCelsius",
                day.maxTemperatureCelsius());
        attributes.put(
                "minTemperatureCelsius",
                day.minTemperatureCelsius());
        attributes.put(
                "precipitationProbabilityMaxPercent",
                day.precipitationProbabilityMaxPercent());
        return attributes;
    }

    /**
     * Persists one search run and its candidate sources.
     *
     * @param invocationId ToolInvocation ID
     * @param command Tool invocation command
     * @param query structured web search query
     * @param results search candidates
     * @return SearchRun ID
     */
    private UUID recordSearchRun(
            UUID invocationId,
            ToolInvocationCommand command,
            WebSearchQuery query,
            List<WebSearchResult> results) {
        UUID searchRunId = UUID.randomUUID();
        WebResearchContext researchContext = webResearchContext(
                invocationId,
                command);
        // SearchRun 是“搜了什么”的审计锚点；Candidate 只是候选，不等同于已采信证据。
        webResearchStore.insertSearchRun(
                searchRunId,
                researchContext,
                query,
                results.size());
        int fallbackRank = 1;
        for (var result : results) {
            webResearchStore.insertSearchCandidate(
                    UUID.randomUUID(),
                    searchRunId,
                    researchContext,
                    rankedResult(result, fallbackRank++));
        }
        return searchRunId;
    }

    /**
     * 为 legacy 测试或异常搜索客户端补齐稳定 rank。
     */
    private WebSearchResult rankedResult(
            WebSearchResult result,
            int fallbackRank) {
        if (result.rank() > 0) {
            return result;
        }
        return new WebSearchResult(
                result.title(),
                result.url(),
                result.snippet(),
                result.publishedAt(),
                result.provider(),
                fallbackRank,
                result.sourceType());
    }

    /**
     * 持久化已读取的网页来源。
     *
     * @param invocationId ToolInvocation ID
     * @param command Tool 调用命令
     * @param document 已清洗来源文档
     * @return SourceDocument ID
     */
    private UUID recordSourceDocument(
            UUID invocationId,
            ToolInvocationCommand command,
            WebSourceDocument document) {
        UUID sourceDocumentId = UUID.randomUUID();
        // 来源文档挂在 ToolInvocation 下，Agent Path 才能解释“哪个工具读了哪个来源”。
        webResearchStore.insertSourceDocument(
                sourceDocumentId,
                webResearchContext(invocationId, command),
                document);
        return sourceDocumentId;
    }

    /**
     * 按抽取顺序持久化网页证据片段。
     *
     * @param sourceDocumentId 来源文档 ID
     * @param invocationId ToolInvocation ID
     * @param command Tool 调用命令
     * @param evidence 证据片段
     */
    private void recordEvidenceItems(
            UUID sourceDocumentId,
            UUID invocationId,
            ToolInvocationCommand command,
            List<WebEvidenceItem> evidence) {
        int rank = 1;
        for (WebEvidenceItem item : evidence) {
            // rank_no 保留模型看到证据的顺序，后续评测可复盘引用来源是否稳定。
            webResearchStore.insertEvidenceItem(
                    UUID.randomUUID(),
                    sourceDocumentId,
                    webResearchContext(invocationId, command),
                    item,
                    rank++);
        }
    }

    /**
     * 从 ToolInvocationCommand 生成 Web Research 追踪上下文。
     *
     * @param invocationId ToolInvocation ID
     * @param command Tool 调用命令
     * @return Web Research 上下文
     */
    private WebResearchContext webResearchContext(
            UUID invocationId,
            ToolInvocationCommand command) {
        return new WebResearchContext(
                invocationId,
                command.jobId(),
                command.taskId(),
                command.taskRunId(),
                command.loopRunId(),
                command.loopNodeId());
    }

    /**
     * 从幂等结果重建 ToolResult。
     */
    private ToolResult fromExisting(Map<String, Object> row) {
        String resultJson = String.valueOf(row.get("resultJson"));
        Map<String, Object> attributes = resultJson == null
                || "null".equals(resultJson)
                ? Map.of()
                : readMap(resultJson);
        boolean completed = "COMPLETED".equals(row.get("status"));
        boolean success = completed
                && Boolean.parseBoolean(
                        String.valueOf(attributes.getOrDefault(
                                "success",
                                "true")));
        String content = String.valueOf(attributes.getOrDefault(
                "stdout",
                ""));
        if (!success && content.isBlank()) {
            content = String.valueOf(attributes.getOrDefault(
                    "message",
                    row.getOrDefault("errorMessage", "")));
        }
        return new ToolResult(
                UUID.fromString(String.valueOf(row.get("id"))),
                success,
                completed
                        ? "Idempotent tool invocation reused"
                        : "Idempotent failed tool invocation reused",
                content,
                attributes);
    }

    /** 读取必填参数。 */
    private String required(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new RuntimeStateException(
                    "TOOL_ARGUMENT_MISSING",
                    "Tool argument is required: " + key);
        }
        return String.valueOf(value);
    }

    /**
     * Reads an optional integer argument.
     */
    private Integer optionalInt(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    /**
     * Reads a string list argument that may arrive as an array or comma text.
     */
    private List<String> stringList(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .filter(item -> !item.isBlank())
                    .toList();
        }
        return java.util.Arrays.stream(String.valueOf(value).split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    /**
     * 获取当前 Tool 调用所属 Conversation。
     *
     * @param context LoopNode 上下文
     * @return Conversation ID
     */
    private UUID requireConversation(RunExecutionContext context) {
        if (context == null) {
            throw new RuntimeStateException(
                    "TOOL_CONTEXT_REQUIRED",
                    "File tools require Loop context");
        }
        return toolStore.findConversationIdByJobId(context.jobId())
                .orElseThrow(() -> new RuntimeStateException(
                        "CONVERSATION_NOT_FOUND",
                        "Job conversation is unavailable: "
                                + context.jobId()));
    }

    /**
     * 读取整数参数。
     */
    private int intArgument(
            Map<String, Object> arguments,
            String key,
            int fallback) {
        Object value = arguments.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(
                    String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /** 解析 JSON Map。 */
    /** 解析澄清原因，非法值降级为 Tool 参数缺失。 */
    private ClarificationReasonType reasonType(Map<String, Object> arguments) {
        Object value = arguments.get("reasonType");
        if (value == null || String.valueOf(value).isBlank()) {
            return ClarificationReasonType.TOOL_ARGUMENT_MISSING;
        }
        try {
            return ClarificationReasonType.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException ignored) {
            return ClarificationReasonType.TOOL_ARGUMENT_MISSING;
        }
    }

    /**
     * 读取 Tool 参数中的澄清合同。
     *
     * <p>当前 Runtime 会传入 {@code contractJson} 字符串；后续模型/Skill 如果直接传
     * {@code contract} 对象，也会在这里序列化为同一持久化字段。</p>
     *
     * @param arguments Tool 参数
     * @return 合同 JSON
     */
    private String clarificationContractJson(Map<String, Object> arguments) {
        Object contractJson = arguments.get("contractJson");
        if (contractJson != null
                && !String.valueOf(contractJson).isBlank()) {
            return String.valueOf(contractJson).trim();
        }
        Object contract = arguments.get("contract");
        if (contract != null) {
            return json(contract);
        }
        return "{}";
    }

    /**
     * 解析 JSON Map，解析失败时返回空 Map 以保持幂等重放安全。
     *
     * @param json JSON 字符串
     * @return 解析后的 Map
     */
    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    /**
     * 序列化 Tool 载荷。
     *
     * @param value 待序列化对象
     * @return JSON 字符串
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null
                    ? Map.of()
                    : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize tool payload",
                    exception);
        }
    }

    /**
     * 截断外部进程输出，避免审计载荷过大。
     *
     * @param value 原始输出
     * @return 截断后的输出
     */
    private String abbreviate(String value) {
        if (value.length() <= MAX_OUTPUT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_OUTPUT_LENGTH);
    }

    /**
     * 脚本进程结果。
     *
     * @param exitCode 退出码
     * @param stdout 标准输出
     * @param stderr 标准错误
     */
    private record ProcessResult(
            int exitCode,
            String stdout,
            String stderr) {
    }
}
