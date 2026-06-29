package com.funjson.metaagent.provider.infrastructure.deepseek;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.provider.application.ProviderConfigService;
import com.funjson.metaagent.provider.application.ModelCatalogService;
import com.funjson.metaagent.provider.application.port.out.ProviderConfigStore;
import com.funjson.metaagent.provider.application.port.out.ProviderConnectionTestPort;
import com.funjson.metaagent.provider.domain.DeepSeekCallException;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;
import com.funjson.metaagent.provider.domain.ModelThinkingMode;
import com.funjson.metaagent.provider.domain.ModelToolCall;
import com.funjson.metaagent.provider.domain.ModelToolSpec;
import com.funjson.metaagent.provider.infrastructure.persistence.mybatis.ModelCallRepository;

/**
 * DeepSeek HTTP Adapter。
 *
 * <p>该类只负责传输、响应解析和调用审计；Prompt 内容由 PromptRegistry 生成，
 * Secret 由 ProviderConfigService 解析。</p>
 */
@Component
public class DeepSeekClient implements ProviderConnectionTestPort {

    private final ProviderConfigService configService;
    private final ModelCatalogService modelCatalog;
    private final ModelCallRepository modelCallRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建 DeepSeek Client。
     *
     * @param configService Provider 配置服务
     * @param modelCatalog 模型目录
     * @param modelCallRepository 模型调用审计 Repository
     * @param objectMapper JSON 解析器
     */
    public DeepSeekClient(
            ProviderConfigService configService,
            ModelCatalogService modelCatalog,
            ModelCallRepository modelCallRepository,
            ObjectMapper objectMapper) {
        this.configService = configService;
        this.modelCatalog = modelCatalog;
        this.modelCallRepository = modelCallRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * @return Provider ID
     */
    @Override
    public String providerId() {
        return "deepseek";
    }

    /**
     * 调用 DeepSeek Chat Completions。
     *
     * @param request 框架无关模型请求
     * @param requestSecretOverride 请求级密钥
     * @return 模型响应
     */
    public ModelResponse generate(
            ModelRequest request,
            String requestSecretOverride) {
        ProviderConfigStore.ProviderConfig config =
                configService.requireDeepSeek();
        String secret = configService.resolveSecret("deepseek", requestSecretOverride);
        String modelName = modelName(request, config);
        long started = System.nanoTime();
        String fingerprint = request.prompt().contentHash();

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", modelName);
            requestBody.put("messages", List.of(
                    Map.of(
                            "role", "system",
                            "content", request.prompt().systemMessage()),
                    Map.of(
                            "role", "user",
                            "content", request.prompt().userMessage())));
            // Reasoner 模型本身就是推理模型，不支持 Function Calling；
            // 普通模型则按请求暴露工具 Schema。
            if (!isReasoner(modelName)) {
                requestBody.put(
                        "thinking",
                        Map.of("type", thinkingType(request.thinkingMode())));
                if (!request.tools().isEmpty()) {
                    requestBody.put("tools", toolSchemas(request.tools()));
                    requestBody.put("tool_choice", "auto");
                }
            }
            requestBody.put("max_tokens", request.maxTokens());
            requestBody.put("stream", false);

            // Secret 只进入 Authorization Header，不进入日志、事件、Prompt 或 Checkpoint。
            String responseBody = WebClient.builder()
                    .baseUrl(config.baseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secret)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            return response.bodyToMono(String.class);
                        }
                        return response.releaseBody().then(Mono.error(new DeepSeekCallException(
                                "DEEPSEEK_HTTP_" + response.statusCode().value(),
                                "DeepSeek request failed with HTTP " + response.statusCode().value())));
                    })
                    .timeout(Duration.ofSeconds(60))
                    .block();

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("choices").path(0).path("message");
            String content = message.path("content").asText("");
            String reasoningContent =
                    message.path("reasoning_content").asText("");
            String finishReason = root.path("choices").path(0).path("finish_reason").asText();
            String responseModel = root.path("model").asText(modelName);
            List<ModelToolCall> toolCalls = parseToolCalls(
                    message.path("tool_calls"),
                    request.tools());
            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
            long latencyMs = elapsedMillis(started);
            // 审计只保存 Prompt 引用和哈希，不保存完整敏感上下文或密钥。
            modelCallRepository.insert(
                    UUID.randomUUID(),
                    request.taskRunId(),
                    request.loopNodeId(),
                    "deepseek",
                    responseModel,
                    fingerprint,
                    request.prompt().promptId(),
                    request.prompt().version(),
                    request.prompt().contentHash(),
                    "COMPLETED",
                    promptTokens,
                    completionTokens,
                    latencyMs,
                    null);
            return new ModelResponse(
                    "deepseek",
                    responseModel,
                    content,
                    finishReason,
                    toolCalls,
                    reasoningContent);
        } catch (DeepSeekCallException exception) {
            recordFailure(request, modelName, fingerprint, started, exception.code());
            throw exception;
        } catch (JsonProcessingException exception) {
            recordFailure(
                    request,
                    modelName,
                    fingerprint,
                    started,
                    "DEEPSEEK_RESPONSE_INVALID");
            throw new DeepSeekCallException(
                    "DEEPSEEK_RESPONSE_INVALID",
                    "DeepSeek returned an invalid JSON response");
        } catch (RuntimeException exception) {
            recordFailure(
                    request,
                    modelName,
                    fingerprint,
                    started,
                    "DEEPSEEK_CLIENT_ERROR");
            throw new DeepSeekCallException(
                    "DEEPSEEK_CLIENT_ERROR",
                    "DeepSeek request failed");
        }
    }

    /**
     * 保存失败调用审计。
     *
     * @param request 模型请求
     * @param model 模型名
     * @param fingerprint 请求指纹
     * @param started 开始纳秒
     * @param errorCode 错误码
     */
    private void recordFailure(
            ModelRequest request,
            String model,
            String fingerprint,
            long started,
            String errorCode) {
        modelCallRepository.insert(
                UUID.randomUUID(),
                request.taskRunId(),
                request.loopNodeId(),
                "deepseek",
                model,
                fingerprint,
                request.prompt().promptId(),
                request.prompt().version(),
                request.prompt().contentHash(),
                "FAILED",
                null,
                null,
                elapsedMillis(started),
                errorCode);
    }

    /**
     * 计算调用延迟。
     *
     * @param started 开始纳秒
     * @return 毫秒
     */
    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    /**
     * @return DeepSeek 当前模型是否支持原生工具调用
     */
    public boolean supportsNativeToolCalling() {
        ProviderConfigStore.ProviderConfig config =
                configService.requireDeepSeek();
        return !isReasoner(config.modelName());
    }

    /**
     * @param modelId 框架模型 ID
     * @return 指定 DeepSeek 模型是否支持工具调用
     */
    public boolean supportsNativeToolCalling(String modelId) {
        return modelCatalog.find(modelId)
                .map(model -> model.capabilities().toolCalling())
                .orElseGet(this::supportsNativeToolCalling);
    }

    /**
     * @return DeepSeek 当前模型是否可能返回 reasoning_content
     */
    public boolean supportsThinkingMode() {
        return true;
    }

    /**
     * @param modelId 框架模型 ID
     * @return 指定 DeepSeek 模型是否支持 thinking/reasoning
     */
    public boolean supportsThinkingMode(String modelId) {
        return modelCatalog.find(modelId)
                .map(model -> model.capabilities().thinkingMode()
                        || model.capabilities().reasoning())
                .orElse(true);
    }

    /**
     * 解析本次请求实际调用的厂商模型名。
     */
    private String modelName(
            ModelRequest request,
            ProviderConfigStore.ProviderConfig config) {
        return modelCatalog.find(request.modelId())
                .filter(model -> "deepseek".equals(model.providerId()))
                .map(model -> model.providerModel())
                .orElse(config.modelName());
    }

    /**
     * 判断是否为不支持 Function Calling 的 DeepSeek reasoner 模型。
     */
    private boolean isReasoner(String modelName) {
        return modelName != null
                && modelName.toLowerCase().contains("reasoner");
    }

    /**
     * 映射框架思考模式到 Provider 参数。
     */
    private String thinkingType(ModelThinkingMode mode) {
        return switch (mode == null ? ModelThinkingMode.DISABLED : mode) {
            case ENABLED -> "enabled";
            case AUTO -> "auto";
            case DISABLED -> "disabled";
        };
    }

    /**
     * 构造 DeepSeek/OpenAI-compatible tools payload。
     */
    private List<Map<String, Object>> toolSchemas(List<ModelToolSpec> tools) {
        return tools.stream()
                .map(tool -> {
                    Map<String, Object> function = new LinkedHashMap<>();
                    function.put("name", tool.functionName());
                    function.put("description", tool.description());
                    function.put("parameters", jsonObject(tool.inputSchemaJson()));
                    return Map.of(
                            "type", "function",
                            "function", function);
                })
                .toList();
    }

    /**
     * 解析模型返回的 tool_calls，并映射回内部 Tool ID。
     */
    private List<ModelToolCall> parseToolCalls(
            JsonNode toolCallsNode,
            List<ModelToolSpec> tools) {
        if (!toolCallsNode.isArray() || toolCallsNode.isEmpty()) {
            return List.of();
        }
        Map<String, ModelToolSpec> byFunctionName = tools.stream()
                .collect(Collectors.toMap(
                        ModelToolSpec::functionName,
                        tool -> tool,
                        (left, right) -> left));
        java.util.ArrayList<ModelToolCall> calls = new java.util.ArrayList<>();
        for (JsonNode node : toolCallsNode) {
            JsonNode function = node.path("function");
            String functionName = function.path("name").asText("");
            ModelToolSpec spec = byFunctionName.get(functionName);
            if (spec == null) {
                continue;
            }
            calls.add(new ModelToolCall(
                    node.path("id").asText(""),
                    spec.toolId(),
                    functionName,
                    arguments(function.path("arguments").asText("{}"))));
        }
        return List.copyOf(calls);
    }

    /**
     * JSON Schema 必须作为对象传给 Provider，而不是字符串。
     */
    private Map<String, Object> jsonObject(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    /**
     * 解析 tool_call arguments。
     */
    private Map<String, Object> arguments(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return new HashMap<>();
        }
    }
}
