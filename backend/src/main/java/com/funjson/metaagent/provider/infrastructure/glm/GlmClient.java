package com.funjson.metaagent.provider.infrastructure.glm;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.provider.application.ModelCatalogService;
import com.funjson.metaagent.provider.application.ProviderConfigService;
import com.funjson.metaagent.provider.application.port.out.ProviderConfigStore;
import com.funjson.metaagent.provider.application.port.out.ProviderConnectionTestPort;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;
import com.funjson.metaagent.provider.domain.ModelThinkingMode;
import com.funjson.metaagent.provider.domain.ModelToolCall;
import com.funjson.metaagent.provider.domain.ModelToolSpec;
import com.funjson.metaagent.provider.domain.ProviderCallException;
import com.funjson.metaagent.provider.infrastructure.persistence.mybatis.ModelCallRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * GLM / Zhipu OpenAI-compatible HTTP Adapter。
 */
@Component
public class GlmClient implements ProviderConnectionTestPort {

    private final ProviderConfigService configService;
    private final ModelCatalogService modelCatalog;
    private final ModelCallRepository modelCallRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建 GLM Client。
     *
     * @param configService Provider 配置服务
     * @param modelCatalog 模型目录
     * @param modelCallRepository 模型调用审计 Repository
     * @param objectMapper JSON Mapper
     */
    public GlmClient(
            ProviderConfigService configService,
            ModelCatalogService modelCatalog,
            ModelCallRepository modelCallRepository,
            ObjectMapper objectMapper) {
        this.configService = configService;
        this.modelCatalog = modelCatalog;
        this.modelCallRepository = modelCallRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerId() {
        return "glm";
    }

    @Override
    public ModelResponse generate(
            ModelRequest request,
            String requestSecretOverride) {
        ProviderConfigStore.ProviderConfig config =
                configService.requireProvider("glm");
        String modelName = modelName(request, config);
        String secret = configService.resolveSecret("glm", requestSecretOverride);
        long started = System.nanoTime();
        String fingerprint = request.prompt().contentHash();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("messages", List.of(
                    Map.of(
                            "role", "system",
                            "content", request.prompt().systemMessage()),
                    Map.of(
                            "role", "user",
                            "content", request.prompt().userMessage())));
            if (supportsThinkingMode(request.modelId())) {
                body.put("thinking", Map.of("type", thinkingType(request.thinkingMode())));
            }
            if (!request.tools().isEmpty()
                    && supportsNativeToolCalling(request.modelId())) {
                body.put("tools", toolSchemas(request.tools()));
                body.put("tool_choice", "auto");
            }
            body.put("max_tokens", request.maxTokens());
            body.put("stream", false);

            String responseBody = WebClient.builder()
                    .baseUrl(config.baseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secret)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            return response.bodyToMono(String.class);
                        }
                        return response.releaseBody().then(Mono.error(
                                new ProviderCallException(
                                        "GLM_HTTP_" + response.statusCode().value(),
                                        "GLM request failed with HTTP "
                                                + response.statusCode().value())));
                    })
                    .timeout(Duration.ofSeconds(60))
                    .block();

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("choices").path(0).path("message");
            String content = message.path("content").asText("");
            String reasoningContent =
                    message.path("reasoning_content").asText("");
            String finishReason = root.path("choices").path(0)
                    .path("finish_reason").asText();
            String responseModel = root.path("model").asText(modelName);
            List<ModelToolCall> toolCalls = parseToolCalls(
                    message.path("tool_calls"),
                    request.tools());
            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
            long latencyMs = elapsedMillis(started);
            modelCallRepository.insert(
                    UUID.randomUUID(),
                    request.taskRunId(),
                    request.loopNodeId(),
                    "glm",
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
                    "glm",
                    responseModel,
                    content,
                    finishReason,
                    toolCalls,
                    reasoningContent);
        } catch (ProviderCallException exception) {
            recordFailure(request, modelName, fingerprint, started, exception.code());
            throw exception;
        } catch (JsonProcessingException exception) {
            recordFailure(request, modelName, fingerprint, started, "GLM_RESPONSE_INVALID");
            throw new ProviderCallException(
                    "GLM_RESPONSE_INVALID",
                    "GLM returned an invalid JSON response");
        } catch (RuntimeException exception) {
            recordFailure(request, modelName, fingerprint, started, "GLM_CLIENT_ERROR");
            throw new ProviderCallException(
                    "GLM_CLIENT_ERROR",
                    "GLM request failed");
        }
    }

    /**
     * @param modelId 框架模型 ID
     * @return 是否支持原生工具调用
     */
    public boolean supportsNativeToolCalling(String modelId) {
        return modelCatalog.find(modelId)
                .map(model -> model.capabilities().toolCalling())
                .orElse(true);
    }

    /**
     * @param modelId 框架模型 ID
     * @return 是否支持 thinking mode
     */
    public boolean supportsThinkingMode(String modelId) {
        return modelCatalog.find(modelId)
                .map(model -> model.capabilities().thinkingMode()
                        || model.capabilities().reasoning())
                .orElse(true);
    }

    /**
     * 保存失败调用审计。
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
                "glm",
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
     * 解析实际厂商模型名。
     */
    private String modelName(
            ModelRequest request,
            ProviderConfigStore.ProviderConfig config) {
        return modelCatalog.find(request.modelId())
                .filter(model -> "glm".equals(model.providerId()))
                .map(model -> model.providerModel())
                .orElse(config.modelName());
    }

    /**
     * 映射 thinking mode。
     */
    private String thinkingType(ModelThinkingMode mode) {
        return switch (mode == null ? ModelThinkingMode.DISABLED : mode) {
            case ENABLED -> "enabled";
            case AUTO -> "auto";
            case DISABLED -> "disabled";
        };
    }

    /**
     * 构造 OpenAI-compatible tools payload。
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
     * 解析 tool_calls。
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
     * JSON Schema 字符串转对象。
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
     * tool arguments 字符串转对象。
     */
    private Map<String, Object> arguments(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    /**
     * @return 调用耗时毫秒
     */
    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }
}
