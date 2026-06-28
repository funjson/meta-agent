package com.funjson.metaagent.provider.infrastructure.deepseek;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.provider.application.ProviderConfigService;
import com.funjson.metaagent.provider.application.port.out.ProviderConfigStore;
import com.funjson.metaagent.provider.application.port.out.ProviderConnectionTestPort;
import com.funjson.metaagent.provider.domain.DeepSeekCallException;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;
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
    private final ModelCallRepository modelCallRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建 DeepSeek Client。
     *
     * @param configService Provider 配置服务
     * @param modelCallRepository 模型调用审计 Repository
     * @param objectMapper JSON 解析器
     */
    public DeepSeekClient(
            ProviderConfigService configService,
            ModelCallRepository modelCallRepository,
            ObjectMapper objectMapper) {
        this.configService = configService;
        this.modelCallRepository = modelCallRepository;
        this.objectMapper = objectMapper;
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
        String secret = configService.resolveSecret(requestSecretOverride);
        long started = System.nanoTime();
        String fingerprint = request.prompt().contentHash();

        try {
            // Secret 只进入 Authorization Header，不进入日志、事件、Prompt 或 Checkpoint。
            String responseBody = WebClient.builder()
                    .baseUrl(config.baseUrl())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secret)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .bodyValue(Map.of(
                            "model", config.modelName(),
                            "messages", List.of(
                                    Map.of(
                                            "role", "system",
                                            "content", request.prompt().systemMessage()),
                                    Map.of(
                                            "role", "user",
                                            "content", request.prompt().userMessage())),
                            "thinking", Map.of("type", "disabled"),
                            "max_tokens", request.maxTokens(),
                            "stream", false))
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
            String content = root.path("choices").path(0).path("message").path("content").asText();
            String finishReason = root.path("choices").path(0).path("finish_reason").asText();
            String responseModel = root.path("model").asText(config.modelName());
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
            return new ModelResponse("deepseek", responseModel, content, finishReason);
        } catch (DeepSeekCallException exception) {
            recordFailure(request, config.modelName(), fingerprint, started, exception.code());
            throw exception;
        } catch (JsonProcessingException exception) {
            recordFailure(
                    request,
                    config.modelName(),
                    fingerprint,
                    started,
                    "DEEPSEEK_RESPONSE_INVALID");
            throw new DeepSeekCallException(
                    "DEEPSEEK_RESPONSE_INVALID",
                    "DeepSeek returned an invalid JSON response");
        } catch (RuntimeException exception) {
            recordFailure(
                    request,
                    config.modelName(),
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
}
