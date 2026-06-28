package com.funjson.metaagent.intent.infrastructure.ai;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.intent.application.port.out.ModelPendingInteractionRouterPort;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import com.funjson.metaagent.intent.domain.PendingInteractionRoute;
import com.funjson.metaagent.intent.domain.PendingInteractionRouteType;
import com.funjson.metaagent.intent.domain.PendingInteractionRoutingRequest;
import com.funjson.metaagent.prompt.application.PromptRegistry;
import com.funjson.metaagent.prompt.domain.PromptUseCase;
import com.funjson.metaagent.provider.application.ModelProviderRegistry;
import com.funjson.metaagent.provider.application.port.out.ProviderSecretPort;
import com.funjson.metaagent.provider.domain.ModelRequest;
import org.springframework.stereotype.Component;

/**
 * 使用真实模型完成 Pending Interaction 的结构化路由和事实抽取。
 *
 * <p>该适配器只返回系统合同对象，不直接生成用户可见消息；Control 层会根据
 * routeType 决定是否展示 userFacingMessage。</p>
 */
@Component
public class ModelPendingInteractionRouter
        implements ModelPendingInteractionRouterPort {

    private final ModelProviderRegistry providerRegistry;
    private final ProviderSecretPort secretStore;
    private final PromptRegistry promptRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 创建模型等待交互 Router。
     *
     * @param providerRegistry 模型 Provider Registry
     * @param secretStore Provider 密钥状态
     * @param promptRegistry Prompt Registry
     * @param objectMapper JSON 解析器
     */
    public ModelPendingInteractionRouter(
            ModelProviderRegistry providerRegistry,
            ProviderSecretPort secretStore,
            PromptRegistry promptRegistry,
            ObjectMapper objectMapper) {
        this.providerRegistry = providerRegistry;
        this.secretStore = secretStore;
        this.promptRegistry = promptRegistry;
        this.objectMapper = objectMapper;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<PendingInteractionRoute> route(
            PendingInteractionRoutingRequest request) {
        if (!request.modelRoutingAllowed() || !secretStore.configured()) {
            return Optional.empty();
        }
        try {
            var prompt = promptRegistry.render(
                    PromptUseCase.CONTROL_PENDING_INTERACTION_ROUTING,
                    Map.of(
                            "conversationContext",
                            request.conversationContext(),
                            "candidateJson",
                            candidateJson(request),
                            "userMessage",
                            request.userMessage()));
            var response = providerRegistry.require("deepseek")
                    .generate(new ModelRequest(
                            null,
                            null,
                            abbreviate(request.userMessage(), 180),
                            prompt,
                            768));
            return Optional.of(parse(response.content()));
        } catch (RuntimeException exception) {
            // 前置路由失败不应阻断主聊天入口，交给本地保守匹配兜底。
            return Optional.empty();
        }
    }

    /**
     * 把候选快照序列化给模型。
     *
     * @param request 路由请求
     * @return 候选 JSON
     */
    private String candidateJson(PendingInteractionRoutingRequest request) {
        try {
            return objectMapper.writeValueAsString(request.candidates());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to serialize pending interaction candidates",
                    exception);
        }
    }

    /**
     * 解析模型 JSON 合同。
     *
     * @param content 模型输出
     * @return 路由结果
     */
    private PendingInteractionRoute parse(String content) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(content));
            PendingInteractionRouteType routeType =
                    PendingInteractionRouteType.valueOf(
                            root.path("routeType").asText("NEW_INTENT"));
            UUID targetId = parseUuid(root.path("targetId").asText(null));
            PendingInteractionFacts facts = new PendingInteractionFacts(
                    parseFacts(root.path("facts")),
                    parseStringList(root.path("missingFields")),
                    root.path("answerSummary").asText(""));
            return new PendingInteractionRoute(
                    routeType,
                    targetId,
                    root.path("confidence").asDouble(0.5),
                    root.path("answerText").asText(""),
                    facts,
                    root.path("userFacingMessage").asText(""),
                    root.path("auditSummary").asText(""));
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Pending interaction model returned an invalid contract",
                    exception);
        }
    }

    /**
     * 解析 facts 对象，只保留非空字符串值。
     *
     * @param node facts JSON 节点
     * @return 稳定字段 Map
     */
    private Map<String, String> parseFacts(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            String value = entry.getValue().asText("").trim();
            if (!value.isBlank()) {
                result.put(entry.getKey(), value);
            }
        });
        return result;
    }

    /**
     * 解析字符串数组。
     *
     * @param node JSON 节点
     * @return 字符串列表
     */
    private List<String> parseStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        try {
            return Arrays.stream(objectMapper.treeToValue(
                    node,
                    String[].class)).toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    /**
     * 解析可空 UUID。
     *
     * @param value UUID 字符串
     * @return UUID 或空
     */
    private UUID parseUuid(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        return UUID.fromString(value);
    }

    /**
     * 移除模型可能附加的 Markdown JSON 围栏。
     *
     * @param value 原始输出
     * @return JSON 文本
     */
    private String stripCodeFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    /**
     * 截断审计摘要。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 摘要
     */
    private String abbreviate(String value, int maxLength) {
        String normalized = value == null
                ? ""
                : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength - 3) + "...";
    }
}
