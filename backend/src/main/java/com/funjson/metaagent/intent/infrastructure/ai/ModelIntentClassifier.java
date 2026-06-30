package com.funjson.metaagent.intent.infrastructure.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.intent.domain.IntentRecognitionRequest;
import com.funjson.metaagent.intent.domain.IntentRiskLevel;
import com.funjson.metaagent.intent.domain.IntentType;
import com.funjson.metaagent.prompt.application.PromptRegistry;
import com.funjson.metaagent.prompt.domain.PromptUseCase;
import com.funjson.metaagent.provider.application.ModelProviderRegistry;
import com.funjson.metaagent.provider.application.port.out.ProviderSecretPort;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.runtime.application.CurrentTimeContextProvider;
import com.funjson.metaagent.intent.application.port.out.ModelIntentClassifierPort;
import org.springframework.stereotype.Component;

/**
 * 使用真实模型和版本化 Prompt 产生结构化意图。
 *
 * <p>解析失败、模型不可用或当前请求禁用模型分类时返回空，由安全降级分类器接管。
 * 该行为确保意图识别故障不会阻断整个聊天入口。</p>
 */
@Component
public class ModelIntentClassifier implements ModelIntentClassifierPort {

    private final ModelProviderRegistry providerRegistry;
    private final ProviderSecretPort secretStore;
    private final PromptRegistry promptRegistry;
    private final ObjectMapper objectMapper;
    private final CurrentTimeContextProvider currentTimeContextProvider;

    /**
     * 创建模型意图分类器。
     *
     * @param providerRegistry 模型 Provider Registry
     * @param secretStore Provider 密钥状态
     * @param promptRegistry Prompt Registry
     * @param objectMapper JSON 解析器
     */
    public ModelIntentClassifier(
            ModelProviderRegistry providerRegistry,
            ProviderSecretPort secretStore,
            PromptRegistry promptRegistry,
            ObjectMapper objectMapper,
            CurrentTimeContextProvider currentTimeContextProvider) {
        this.providerRegistry = providerRegistry;
        this.secretStore = secretStore;
        this.promptRegistry = promptRegistry;
        this.objectMapper = objectMapper;
        this.currentTimeContextProvider = currentTimeContextProvider;
    }

    /**
     * 调用 DeepSeek 输出结构化意图 JSON。
     *
     * @param request 意图识别请求
     * @return 成功解析的模型分类结果
     */
    @Override
    public Optional<IntentRecognition> classify(IntentRecognitionRequest request) {
        if (!request.modelClassificationAllowed() || !secretStore.configured()) {
            return Optional.empty();
        }
        try {
            var prompt = promptRegistry.render(
                    PromptUseCase.CONTROL_INTENT_RECOGNITION,
                    Map.of(
                            "conversationContext",
                            request.conversationContext(),
                            "currentTime",
                            currentTimeContextProvider.current().promptText(),
                            "userMessage",
                            request.userMessage()));
            var response = providerRegistry.require("deepseek").generate(new ModelRequest(
                    null,
                    null,
                    abbreviate(request.userMessage(), 180),
                    prompt,
                    512));
            return Optional.of(parse(response.content()));
        } catch (RuntimeException exception) {
            // 意图识别属于前置增强能力。外部模型故障时必须让安全降级策略继续工作。
            return Optional.empty();
        }
    }

    /**
     * 解析并约束模型返回的 JSON。
     *
     * @param content 模型响应
     * @return 结构化意图
     */
    private IntentRecognition parse(String content) {
        try {
            String json = stripCodeFence(content);
            JsonNode root = objectMapper.readTree(json);
            IntentType intentType = IntentType.valueOf(
                    root.path("intentType").asText("CREATE_JOB"));
            double confidence = Math.max(
                    0,
                    Math.min(1, root.path("confidence").asDouble(0.5)));
            List<String> constraints = root.path("constraints").isArray()
                    ? Arrays.stream(objectMapper.treeToValue(
                            root.path("constraints"),
                            String[].class)).toList()
                    : List.of();
            List<String> labels = root.path("labels").isArray()
                    ? Arrays.stream(objectMapper.treeToValue(
                            root.path("labels"),
                            String[].class)).toList()
                    : List.of();
            return new IntentRecognition(
                    intentType,
                    confidence,
                    "MODEL:deepseek",
                    root.path("goalSummary").asText(""),
                    root.path("decisionSummary").asText(
                            "模型已返回结构化意图。"),
                    constraints,
                    root.path("requiresClarification").asBoolean(false),
                    root.path("compoundTask").asBoolean(false),
                    parseRisk(root.path("riskLevel").asText("MEDIUM")),
                    labels,
                    root.path("clarificationQuestion").asText(""),
                    root.path("clarificationContract").isObject()
                            ? objectMapper.writeValueAsString(
                                    root.path("clarificationContract"))
                            : "{}");
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Intent model returned an invalid contract",
                    exception);
        }
    }

    /**
     * 解析风险等级，未知值按中风险处理。
     *
     * @param value 模型返回值
     * @return 风险等级
     */
    private IntentRiskLevel parseRisk(String value) {
        try {
            return IntentRiskLevel.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return IntentRiskLevel.MEDIUM;
        }
    }

    /**
     * 移除模型可能附加的 Markdown JSON 围栏。
     *
     * @param value 模型响应
     * @return 纯 JSON 文本
     */
    private String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    /**
     * 截断非敏感审计摘要。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 摘要
     */
    private String abbreviate(String value, int maxLength) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength - 3) + "...";
    }
}
