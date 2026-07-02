package com.funjson.metaagent.intent.infrastructure.ai;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.intent.application.port.out.ModelTurnUnderstandingPort;
import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.intent.domain.IntentRewrite;
import com.funjson.metaagent.intent.domain.IntentRiskLevel;
import com.funjson.metaagent.intent.domain.IntentType;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import com.funjson.metaagent.intent.domain.TurnAction;
import com.funjson.metaagent.intent.domain.TurnActionType;
import com.funjson.metaagent.intent.domain.TurnDependencyType;
import com.funjson.metaagent.intent.domain.TurnIntentEdge;
import com.funjson.metaagent.intent.domain.TurnIntentGraph;
import com.funjson.metaagent.intent.domain.TurnIntentNode;
import com.funjson.metaagent.intent.domain.TurnIntentNodeKind;
import com.funjson.metaagent.intent.domain.TurnRoutingRequest;
import com.funjson.metaagent.intent.domain.TurnTaskType;
import com.funjson.metaagent.intent.domain.TurnUnderstanding;
import com.funjson.metaagent.prompt.application.PromptRegistry;
import com.funjson.metaagent.prompt.domain.PromptUseCase;
import com.funjson.metaagent.provider.application.ModelProviderRegistry;
import com.funjson.metaagent.provider.application.port.out.ProviderSecretPort;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.runtime.application.CurrentTimeContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Model adapter for full user-turn orchestration.
 *
 * <p>The adapter asks the model for a structured {@link TurnIntentGraph}. It
 * never creates Jobs, resumes TaskRuns or renders final task results. If an old
 * model/prompt still returns {@code actions[]}, the parser wraps those actions
 * into graph nodes so the Control layer always receives one graph contract.</p>
 */
@Component
public class ModelTurnUnderstandingAdapter
        implements ModelTurnUnderstandingPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ModelTurnUnderstandingAdapter.class);

    private static final int MAX_TOKENS = 2_200;

    private final ModelProviderRegistry providerRegistry;
    private final ProviderSecretPort secretStore;
    private final PromptRegistry promptRegistry;
    private final ObjectMapper objectMapper;
    private final CurrentTimeContextProvider currentTimeContextProvider;

    /**
     * Creates the adapter.
     *
     * @param providerRegistry model provider registry
     * @param secretStore provider secret store
     * @param promptRegistry prompt registry
     * @param objectMapper JSON mapper
     * @param currentTimeContextProvider current time provider
     */
    public ModelTurnUnderstandingAdapter(
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
     * Calls the model to understand a complete user turn.
     *
     * @param request turn routing request
     * @return parsed understanding when the model contract is valid JSON
     */
    @Override
    public Optional<TurnUnderstanding> understand(TurnRoutingRequest request) {
        if (!request.modelRoutingAllowed() || !secretStore.configured()) {
            return Optional.empty();
        }
        try {
            var prompt = promptRegistry.render(
                    PromptUseCase.CONTROL_TURN_UNDERSTANDING,
                    Map.of(
                            "conversationContext",
                            request.promptView(),
                            "currentTime",
                            currentTimeContextProvider.current().promptText(),
                            "pendingCandidateJson",
                            pendingCandidateJson(request),
                            "userMessage",
                            request.userMessage()));
            var response = providerRegistry.require("deepseek")
                    .generate(new ModelRequest(
                            null,
                            null,
                            abbreviate(request.userMessage(), 180),
                            prompt,
                            MAX_TOKENS));
            return Optional.of(parse(response.content()));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Turn understanding model output was ignored: {}",
                    exception.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parses the model JSON contract.
     */
    private TurnUnderstanding parse(String content) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(content));
            JsonNode nodesNode = root.has("nodes")
                    ? root.path("nodes")
                    : root.path("actions");
            if (!nodesNode.isArray() || nodesNode.isEmpty()) {
                throw new IllegalArgumentException(
                        "Turn understanding has no graph nodes");
            }
            List<TurnIntentNode> nodes = new java.util.ArrayList<>();
            int index = 1;
            for (JsonNode node : nodesNode) {
                nodes.add(parseNode(node, index));
                index++;
            }
            TurnIntentGraph graph = new TurnIntentGraph(
                    nodes,
                    parseEdges(root.path("edges")),
                    root.path("auditSummary").asText(""));
            return new TurnUnderstanding(
                    graph,
                    root.path("auditSummary").asText(""));
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Turn understanding model returned an invalid contract",
                    exception);
        }
    }

    /**
     * Parses one graph node object.
     */
    private TurnIntentNode parseNode(JsonNode node, int index) {
        TurnAction action = parseAction(node);
        String nodeId = node.path("nodeId").asText("node-" + index);
        TurnIntentNodeKind kind = parseNodeKind(
                node.path("nodeKind").asText(""),
                action.actionType());
        TurnTaskType taskType = parseTaskType(
                node.path("taskType").asText(""));
        return new TurnIntentNode(
                nodeId,
                kind,
                taskType,
                node.path("sourceSpan").asText(action.sourceSpan()),
                node.path("canonicalGoal").asText(action.canonicalGoal()),
                parseStringList(node.path("labels")),
                action);
    }

    /**
     * Parses one executable action embedded in a graph node.
     */
    private TurnAction parseAction(JsonNode node) {
        TurnActionType actionType = parseActionType(
                node.path("actionType").asText(""),
                node.path("nodeKind").asText(""));
        PendingInteractionFacts facts = new PendingInteractionFacts(
                parseFacts(node.path("facts")),
                parseStringList(node.path("missingFields")),
                node.path("answerSummary").asText(""));
        IntentRecognition recognition = parseRecognition(node, actionType);
        return new TurnAction(
                actionType,
                parseUuid(node.path("targetId").asText(null)),
                node.path("answerText").asText(""),
                facts,
                recognition,
                node.path("userFacingMessage").asText(""),
                node.path("auditSummary").asText(""),
                node.path("sourceSpan").asText(""),
                node.path("originalText").asText(""),
                node.path("canonicalGoal").asText(""),
                parseRewrite(node.path("rewrite")));
    }

    /**
     * Parses action-level intent recognition metadata.
     */
    private IntentRecognition parseRecognition(
            JsonNode action,
            TurnActionType actionType) {
        JsonNode intent = action.path("intent");
        if (!intent.isObject()) {
            return null;
        }
        IntentType intentType = parseIntentType(
                intent.path("intentType").asText("CREATE_JOB"),
                actionType.name());
        return new IntentRecognition(
                intentType,
                clamp(intent.path("confidence").asDouble(0.7)),
                "MODEL:turn-understanding",
                intent.path("goalSummary").asText(
                        action.path("canonicalGoal").asText("")),
                intent.path("decisionSummary").asText(
                        action.path("auditSummary").asText(
                                "Model understood the current turn node.")),
                parseStringList(intent.path("constraints")),
                intent.path("requiresClarification").asBoolean(false),
                intent.path("compoundTask").asBoolean(false),
                parseRisk(intent.path("riskLevel").asText("MEDIUM")),
                mergeLabels(
                        parseStringList(intent.path("labels")),
                        parseStringList(action.path("labels"))),
                intent.path("clarificationQuestion").asText(""),
                intent.path("clarificationContract").isObject()
                        ? json(intent.path("clarificationContract"))
                        : "{}");
    }

    /**
     * Merges intent-level and node-level labels without changing order.
     */
    private List<String> mergeLabels(List<String> first, List<String> second) {
        java.util.LinkedHashSet<String> labels = new java.util.LinkedHashSet<>();
        labels.addAll(first == null ? List.of() : first);
        labels.addAll(second == null ? List.of() : second);
        return List.copyOf(labels);
    }

    /**
     * Parses graph edges from the model contract.
     */
    private List<TurnIntentEdge> parseEdges(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<TurnIntentEdge> result = new java.util.ArrayList<>();
        for (JsonNode edge : node) {
            result.add(new TurnIntentEdge(
                    edge.path("fromNodeId").asText(""),
                    edge.path("toNodeId").asText(""),
                    parseDependency(edge.path("relationType").asText("")),
                    edge.path("reason").asText("")));
        }
        return List.copyOf(result);
    }

    /**
     * Parses action type from either the new node kind or old action type.
     */
    private TurnActionType parseActionType(String actionType, String nodeKind) {
        if (actionType != null && !actionType.isBlank()) {
            return TurnActionType.valueOf(actionType);
        }
        return switch (parseNodeKind(nodeKind, TurnActionType.CREATE_JOB)) {
            case ANSWER_PENDING -> TurnActionType.ANSWER_PENDING;
            case NEW_JOB, CHAT_RESPONSE -> TurnActionType.CREATE_JOB;
            case DISAMBIGUATION -> TurnActionType.ASK_DISAMBIGUATION;
            case EXPLAIN_PENDING_REQUIREMENTS ->
                    TurnActionType.EXPLAIN_PENDING_REQUIREMENTS;
            case CONTROL_COMMAND -> TurnActionType.CONTROL_MESSAGE;
            case CLARIFICATION_NO_TARGET ->
                    TurnActionType.CLARIFICATION_NO_TARGET;
        };
    }

    /**
     * Parses node kind with an action-type fallback.
     */
    private TurnIntentNodeKind parseNodeKind(
            String value,
            TurnActionType fallbackActionType) {
        if (value != null && !value.isBlank()) {
            try {
                return TurnIntentNodeKind.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                // Unknown model node kinds fall back to the executable action.
            }
        }
        return switch (fallbackActionType) {
            case ANSWER_PENDING -> TurnIntentNodeKind.ANSWER_PENDING;
            case CREATE_JOB -> TurnIntentNodeKind.NEW_JOB;
            case ASK_DISAMBIGUATION -> TurnIntentNodeKind.DISAMBIGUATION;
            case EXPLAIN_PENDING_REQUIREMENTS ->
                    TurnIntentNodeKind.EXPLAIN_PENDING_REQUIREMENTS;
            case CONTROL_MESSAGE -> TurnIntentNodeKind.CONTROL_COMMAND;
            case CLARIFICATION_NO_TARGET ->
                    TurnIntentNodeKind.CLARIFICATION_NO_TARGET;
        };
    }

    /**
     * Parses task type with a safe default.
     */
    private TurnTaskType parseTaskType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TurnTaskType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return TurnTaskType.UNKNOWN;
        }
    }

    /**
     * Parses dependency relation with a safe default.
     */
    private TurnDependencyType parseDependency(String value) {
        if (value == null || value.isBlank()) {
            return TurnDependencyType.INDEPENDENT;
        }
        try {
            return TurnDependencyType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return TurnDependencyType.INDEPENDENT;
        }
    }

    /**
     * Parses rewrite audit metadata.
     */
    private IntentRewrite parseRewrite(JsonNode node) {
        if (!node.isObject()) {
            return IntentRewrite.none();
        }
        return new IntentRewrite(
                node.path("changed").asBoolean(false),
                node.path("summary").asText(""),
                parseStringList(node.path("preservedUserFacts")));
    }

    /**
     * Serializes pending candidates for the model prompt.
     */
    private String pendingCandidateJson(TurnRoutingRequest request) {
        try {
            return objectMapper.writeValueAsString(
                    request.pendingCandidates());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to serialize pending candidates",
                    exception);
        }
    }

    /**
     * Parses a facts object into stable string values.
     */
    private Map<String, String> parseFacts(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            String value = entry.getValue().asText("").trim();
            if (!entry.getKey().isBlank() && !value.isBlank()) {
                result.put(entry.getKey(), value);
            }
        });
        return Map.copyOf(result);
    }

    /**
     * Parses a JSON array of strings.
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
     * Parses a nullable UUID.
     */
    private UUID parseUuid(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        return UUID.fromString(value);
    }

    /**
     * Parses risk level with a safe default.
     */
    private IntentRiskLevel parseRisk(String value) {
        try {
            return IntentRiskLevel.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return IntentRiskLevel.MEDIUM;
        }
    }

    /**
     * Parses intent type defensively because models often invent fine-grained
     * values such as WEATHER_QUERY or SEARCH_TASK.
     */
    private IntentType parseIntentType(String value, String actionType) {
        try {
            return IntentType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            if (TurnActionType.CREATE_JOB.name().equals(actionType)) {
                return IntentType.CREATE_JOB;
            }
            if (TurnActionType.ANSWER_PENDING.name().equals(actionType)) {
                return IntentType.CLARIFICATION_ANSWER;
            }
            return IntentType.CHAT_QA;
        }
    }

    /**
     * Clamps model confidence into the expected range.
     */
    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    /**
     * Serializes a JSON node.
     */
    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return "{}";
        }
    }

    /**
     * Removes an optional Markdown JSON fence.
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
     * Produces a bounded audit summary.
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
