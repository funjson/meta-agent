package com.funjson.metaagent.clarification.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.clarification.domain.ClarificationRequest;
import com.funjson.metaagent.intent.domain.PendingInteractionCompletion;
import org.springframework.stereotype.Service;

/**
 * Renders clarification-related user-facing responses.
 *
 * <p>The clarification contract is a system object, while the chat room should
 * only expose natural Chinese questions or hints. Keeping this renderer outside
 * Control prevents the control transaction from also owning presentation rules.</p>
 */
@Service
public class ClarificationUserResponseRenderer {

    private final ObjectMapper objectMapper;

    /**
     * Creates a renderer backed by the application JSON mapper.
     *
     * @param objectMapper JSON mapper used to read clarification contracts
     */
    public ClarificationUserResponseRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Renders a help message for “what else do you need?” turns.
     *
     * @param request current open clarification request
     * @return user-facing Chinese explanation
     */
    public String help(ClarificationRequest request) {
        List<String> slotLabels = contractSlotLabels(
                request.contractJson(),
                true);
        if (slotLabels.isEmpty()) {
            return """
                    可以，我需要补充几类会影响结果的信息：你是谁或你的背景、这份内容用在什么场合、希望正式还是轻松、需要多长，以及有没有必须包含或避免的内容。
                    如果你不想细说，也可以直接说“其他随意”或“按通用模板先写”，我会按默认假设推进。
                    """.trim();
        }
        return """
                可以，我现在主要还需要这些信息：%s。
                如果其中有些你不想细说，可以直接说“其他随意”或“按通用模板先写”，我会用默认假设补齐并继续。
                """.formatted(String.join("、", slotLabels)).trim();
    }

    /**
     * Renders the follow-up clarification message after a partial answer.
     *
     * @param request current clarification request
     * @param completion structural completeness assessment
     * @return user-facing Chinese follow-up
     */
    public String incomplete(
            ClarificationRequest request,
            PendingInteractionCompletion completion) {
        Map<String, String> labels = contractSlotLabelMap(request.contractJson());
        String missing = completion.missingFields().isEmpty()
                ? "还有一些关键信息"
                : completion.missingFields().stream()
                        .map(field -> clarificationFieldLabel(labels, field))
                        .distinct()
                        .reduce((left, right) -> left + "、" + right)
                        .orElse("还有一些关键信息");
        String known = completion.mergedFacts().isEmpty()
                ? ""
                : "我已经收到你补充的一部分信息。\n";
        return """
                %s还差：%s。
                你可以直接补一句话；如果这些你都想让我按通用方式处理，也可以说“其他随意”。
                """.formatted(known, missing).trim();
    }

    /**
     * Reads ordered slot labels from a clarification contract.
     *
     * @param contractJson contract JSON
     * @param requiredOnly whether only required slots should be returned
     * @return ordered user-facing labels
     */
    private List<String> contractSlotLabels(
            String contractJson,
            boolean requiredOnly) {
        return List.copyOf(contractSlotLabelMap(contractJson, requiredOnly)
                .values());
    }

    /**
     * Reads a key-to-label map from a clarification contract.
     *
     * @param contractJson contract JSON
     * @return ordered key-to-label map
     */
    private Map<String, String> contractSlotLabelMap(String contractJson) {
        return contractSlotLabelMap(contractJson, false);
    }

    /**
     * Reads a key-to-label map from a clarification contract.
     *
     * @param contractJson contract JSON
     * @param requiredOnly whether optional slots should be ignored
     * @return ordered key-to-label map
     */
    private Map<String, String> contractSlotLabelMap(
            String contractJson,
            boolean requiredOnly) {
        try {
            JsonNode slots = objectMapper.readTree(safeJson(contractJson))
                    .path("slots");
            if (!slots.isArray()) {
                return Map.of();
            }
            Map<String, String> labels = new LinkedHashMap<>();
            for (JsonNode slot : slots) {
                if (requiredOnly && !slot.path("required").asBoolean(false)) {
                    continue;
                }
                String key = slot.path("key").asText("").trim();
                String label = slot.path("label").asText(
                        slot.path("key").asText(""));
                if (!key.isBlank() && !label.isBlank()) {
                    labels.put(key, label);
                }
            }
            return labels;
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    /**
     * Converts system field names into Chinese labels.
     *
     * @param contractLabels labels read from the clarification contract
     * @param field system field name or model missing field
     * @return user-facing field label
     */
    private String clarificationFieldLabel(
            Map<String, String> contractLabels,
            String field) {
        if (field == null || field.isBlank()) {
            return "关键信息";
        }
        String direct = contractLabels.get(field);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        return fallbackClarificationLabels()
                .getOrDefault(normalizeFieldName(field), field.trim());
    }

    /**
     * Provides stable Chinese labels for common clarification fields.
     *
     * @return normalized system field to Chinese label map
     */
    private Map<String, String> fallbackClarificationLabels() {
        return Map.ofEntries(
                Map.entry("name", "姓名或称呼"),
                Map.entry("username", "姓名或称呼"),
                Map.entry("fullname", "姓名或称呼"),
                Map.entry("background", "背景信息"),
                Map.entry("identity", "身份背景"),
                Map.entry("profile", "个人背景"),
                Map.entry("role", "目标岗位或角色"),
                Map.entry("position", "目标岗位或角色"),
                Map.entry("targetposition", "目标岗位"),
                Map.entry("occupation", "职业或岗位"),
                Map.entry("profession", "职业方向"),
                Map.entry("experience", "工作经验"),
                Map.entry("yearsexperience", "工作年限"),
                Map.entry("education", "教育背景"),
                Map.entry("educationlevel", "学历信息"),
                Map.entry("purpose", "使用场景"),
                Map.entry("usecase", "使用场景"),
                Map.entry("scenario", "使用场景"),
                Map.entry("context", "使用场合"),
                Map.entry("style", "风格偏好"),
                Map.entry("tone", "风格语气"),
                Map.entry("length", "长度要求"),
                Map.entry("wordcount", "字数要求"),
                Map.entry("requirements", "特别要求"),
                Map.entry("mustinclude", "必须包含的内容"),
                Map.entry("mustavoid", "需要避免的内容"),
                Map.entry("skills", "技能特长"),
                Map.entry("contact", "联系方式"),
                Map.entry("phone", "电话"),
                Map.entry("email", "邮箱"),
                Map.entry("city", "所在城市"),
                Map.entry("outputformat", "输出形式"));
    }

    /**
     * Normalizes field names across camelCase, snake_case and free text.
     *
     * @param field raw field name
     * @return normalized comparison key
     */
    private String normalizeFieldName(String field) {
        return field == null
                ? ""
                : field.replaceAll("[_\\-\\s]+", "")
                        .trim()
                        .toLowerCase(java.util.Locale.ROOT);
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
}
