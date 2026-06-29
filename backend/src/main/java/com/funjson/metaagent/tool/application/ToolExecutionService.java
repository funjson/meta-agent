package com.funjson.metaagent.tool.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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
    private final ObjectMapper objectMapper;

    /**
     * 创建 Tool 执行服务。
     *
     * @param toolStore Tool Store
     * @param capabilityApplicationService Capability 应用服务
     * @param clarificationService 澄清服务
     * @param fileAttachmentService 文件附件服务
     * @param webSearchService Web Search 服务
     * @param objectMapper JSON Mapper
     */
    public ToolExecutionService(
            ToolStore toolStore,
            CapabilityApplicationService capabilityApplicationService,
            ClarificationService clarificationService,
            FileAttachmentService fileAttachmentService,
            WebSearchService webSearchService,
            ObjectMapper objectMapper) {
        this.toolStore = toolStore;
        this.capabilityApplicationService = capabilityApplicationService;
        this.clarificationService = clarificationService;
        this.fileAttachmentService = fileAttachmentService;
        this.webSearchService = webSearchService;
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
                invokeInternal(command, context),
                actionType);
    }

    /**
     * 执行 Tool 并维护审计状态。
     */
    @Transactional
    protected ToolResult invokeInternal(
            ToolInvocationCommand command,
            RunExecutionContext context) {
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
            ToolResult result = execute(
                    invocationId,
                    command,
                    context);
            toolStore.complete(
                    invocationId,
                    json(result.attributes()));
            return result;
        } catch (RuntimeException failure) {
            ToolResult result = new ToolResult(
                    invocationId,
                    false,
                    failure.getMessage(),
                    "",
                    Map.of(
                            "errorType",
                            failure.getClass().getSimpleName(),
                            "message",
                            failure.getMessage() == null
                                    ? "tool failed"
                                    : failure.getMessage()));
            toolStore.fail(
                    invocationId,
                    result.summary(),
                    json(result.attributes()));
            throw failure;
        }
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
        var results = webSearchService.search(query, limit);
        String content = results.isEmpty()
                ? "没有返回搜索结果。"
                : results.stream()
                        .map(result -> "- %s\n  url: %s\n  snippet: %s%s"
                                .formatted(
                                        result.title(),
                                        result.url(),
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
                        "query", query,
                        "results", results,
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
            case "web.search" -> ToolType.RETRIEVAL;
            case "file.list", "file.read", "file.search" ->
                    ToolType.RETRIEVAL;
            case "file.write" -> ToolType.FUNCTION;
            default -> ToolType.FUNCTION;
        };
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
        return new ToolResult(
                UUID.fromString(String.valueOf(row.get("id"))),
                "COMPLETED".equals(row.get("status")),
                "Idempotent tool invocation reused",
                String.valueOf(attributes.getOrDefault("stdout", "")),
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
