package com.funjson.metaagent.intent.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.intent.domain.PendingInteractionCompletion;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import org.springframework.stereotype.Service;

/**
 * 对用户补充事实进行合同完整性判断。
 *
 * <p>PendingInteractionRouter 负责“这条消息是不是回答、回答了谁、抽取了什么”；
 * 本策略负责“累计事实是否足以恢复原执行点”。二者分开后，Control 不需要为了
 * 某个具体测试场景继续堆叠自然语言 if 分支。</p>
 */
@Service
public class PendingInteractionCompletionPolicy {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<Slot, List<String>> SLOT_ALIASES =
            Map.of(
                    Slot.NAME,
                    List.of("name", "userName", "fullName", "姓名", "名字"),
                    Slot.BACKGROUND,
                    List.of(
                            "background", "profile", "identity", "role",
                            "occupation", "jobTitle", "industry", "domain",
                            "experience", "yearsExperience", "skills",
                            "背景", "身份", "职业", "岗位", "行业", "经验"),
                    Slot.ROLE,
                    List.of(
                            "role", "occupation", "jobTitle", "position",
                            "targetPosition", "profession", "industry",
                            "domain", "岗位", "职业", "行业", "方向"),
                    Slot.EXPERIENCE,
                    List.of(
                            "experience", "yearsExperience", "workExperience",
                            "background", "经验", "经历", "年限"),
                    Slot.PURPOSE,
                    List.of(
                            "purpose", "useCase", "usage", "scenario",
                            "occasion", "context", "target", "用途",
                            "场景", "场合", "目的"),
                    Slot.STYLE,
                    List.of("style", "tone", "voice", "风格", "语气"),
                    Slot.LENGTH,
                    List.of(
                            "length", "wordCount", "outputLength", "size",
                            "长度", "字数", "篇幅"));

    private static final List<String> REQUIREMENT_ALIASES = List.of(
            "mustInclude", "mustAvoid", "requirements", "constraints",
            "highlights", "avoid", "noSpecialRequirements", "特别要求",
            "突出", "避免");

    /**
     * 判断一次回答是否已经满足澄清问题的结构化合同。
     *
     * @param question 原澄清问题
     * @param blockingSummary 原阻塞摘要
     * @param currentFacts 当前用户消息中抽取的事实
     * @param accumulatedFacts 同一等待项上历史已抽取事实
     * @return 完整性评估
     */
    public PendingInteractionCompletion assess(
            String question,
            String blockingSummary,
            String contractJson,
            PendingInteractionFacts currentFacts,
            Map<String, String> accumulatedFacts) {
        PendingInteractionFacts safeFacts = currentFacts == null
                ? PendingInteractionFacts.empty()
                : currentFacts;
        Map<String, String> mergedFacts = mergeFacts(
                accumulatedFacts,
                safeFacts.facts());
        List<SlotRequirement> contractSlots = contractSlots(contractJson);
        Set<Slot> requiredSlots = contractSlots.isEmpty()
                ? requiredSlots(question, blockingSummary)
                : Set.of();
        List<String> missing = new ArrayList<>();
        boolean acceptedDefaults = hasTruthyValue(
                mergedFacts,
                "userAcceptedDefaults");
        boolean generationDefaultsAllowed = acceptedDefaults
                && looksLikeGenerationClarification(
                        question,
                        blockingSummary,
                        contractSlots);

        if (!contractSlots.isEmpty()) {
            for (SlotRequirement slot : contractSlots) {
                if (slot.blocksResume()
                        && !slotSatisfied(slot, mergedFacts)
                        && !slotSatisfiedByDefaultConsent(
                                slot,
                                acceptedDefaults,
                                generationDefaultsAllowed)) {
                    missing.add(slot.key());
                }
            }
        } else {
            for (Slot slot : requiredSlots) {
                if (!slotSatisfied(slot, mergedFacts)) {
                    missing.add(slot.key());
                }
            }
        }
        for (String field : safeFacts.missingFields()) {
            appendModelMissingField(
                    field,
                    mergedFacts,
                    contractSlots,
                    acceptedDefaults,
                    generationDefaultsAllowed,
                    requiredSlots,
                    missing);
        }

        // 默认授权只允许补齐合同中 defaultable=true 的槽位；
        // 如果仍缺少姓名、用途这类不可默认字段，任务必须继续等待澄清。
        boolean complete = missing.isEmpty();
        return new PendingInteractionCompletion(
                complete,
                mergedFacts,
                List.copyOf(new LinkedHashSet<>(missing)),
                summary(safeFacts.answerSummary(), mergedFacts, missing));
    }

    /**
     * 合并历史事实与当前事实。
     *
     * <p>当前消息是最新用户表达，允许覆盖同名字段；历史事实则补齐多轮澄清中
     * 已经确认但本轮没有重复的信息。</p>
     */
    private Map<String, String> mergeFacts(
            Map<String, String> accumulatedFacts,
            Map<String, String> currentFacts) {
        Map<String, String> merged = new LinkedHashMap<>();
        putAllNonBlank(merged, accumulatedFacts);
        putAllNonBlank(merged, currentFacts);
        return Map.copyOf(merged);
    }

    /** 把非空事实写入目标 Map。 */
    private void putAllNonBlank(
            Map<String, String> target,
            Map<String, String> source) {
        if (source == null) {
            return;
        }
        source.forEach((key, value) -> {
            if (key != null && value != null && !value.isBlank()) {
                target.put(key.trim(), value.trim());
            }
        });
    }

    /**
     * 从澄清问题和阻塞摘要中抽取本次合同关注的稳定槽位。
     */
    private Set<Slot> requiredSlots(
            String question,
            String blockingSummary) {
        String text = normalize(question + " " + blockingSummary);
        Set<Slot> slots = new LinkedHashSet<>();
        if (containsAny(text, "姓名", "名字", "name")) {
            slots.add(Slot.NAME);
        }
        if (containsAny(text, "目标对象", "背景", "个人信息", "详细个人")) {
            slots.add(Slot.BACKGROUND);
        }
        if (containsAny(text, "岗位", "行业", "职业", "角色", "position")) {
            slots.add(Slot.ROLE);
        }
        if (containsAny(text, "经验", "经历", "年限")) {
            slots.add(Slot.EXPERIENCE);
        }
        if (containsAny(text, "使用场景", "用途", "场合", "目的", "use case")) {
            slots.add(Slot.PURPOSE);
        }
        if (containsAny(text, "风格", "语气", "style", "tone")) {
            slots.add(Slot.STYLE);
        }
        if (containsAny(text, "长度", "字数", "篇幅", "几句", "几段", "length")) {
            slots.add(Slot.LENGTH);
        }
        return slots;
    }

    /**
     * 把模型返回的 missingFields 纳入同一套槽位判断。
     */
    private void appendModelMissingField(
            String field,
            Map<String, String> facts,
            List<SlotRequirement> contractSlots,
            boolean acceptedDefaults,
            boolean generationDefaultsAllowed,
            Set<Slot> requiredSlots,
            List<String> missing) {
        if (field == null || field.isBlank()) {
            return;
        }
        String normalized = normalize(field);
        if (isRequirementField(normalized)
                && hasTruthyValue(facts, "noSpecialRequirements")) {
            return;
        }
        SlotRequirement requirement = contractRequirementForField(
                normalized,
                contractSlots);
        if (requirement != null) {
            if (requirement.blocksResume()
                    && !slotSatisfied(requirement, facts)
                    && !slotSatisfiedByDefaultConsent(
                            requirement,
                            acceptedDefaults,
                            generationDefaultsAllowed)) {
                missing.add(requirement.key());
            }
            return;
        }
        if (!contractSlots.isEmpty()) {
            return;
        }
        Slot slot = slotForField(normalized);
        if (slot != null) {
            if ((requiredSlots.isEmpty() || requiredSlots.contains(slot))
                    && !slotSatisfied(slot, facts)) {
                missing.add(slot.key());
            }
            return;
        }
        if (requiredSlots.isEmpty() && !hasExactFact(field, facts)
                && !isOptionalField(normalized)) {
            missing.add(field.trim());
        }
    }

    /** 判断一个稳定槽位是否已被任一别名事实满足。 */
    private boolean slotSatisfied(
            Slot slot,
            Map<String, String> facts) {
        return SLOT_ALIASES.getOrDefault(slot, List.of()).stream()
                .anyMatch(alias -> hasExactFact(alias, facts));
    }

    /** 判断合同槽位是否已满足。 */
    private boolean slotSatisfied(
            SlotRequirement slot,
            Map<String, String> facts) {
        if (hasExactFact(slot.key(), facts)) {
            return true;
        }
        return slot.aliases().stream()
                .anyMatch(alias -> hasExactFact(alias, facts));
    }

    /** 从合同 JSON 中读取槽位要求。 */
    private List<SlotRequirement> contractSlots(String contractJson) {
        try {
            JsonNode slots = objectMapper.readTree(
                    contractJson == null || contractJson.isBlank()
                            ? "{}"
                            : contractJson).path("slots");
            if (!slots.isArray()) {
                return List.of();
            }
            List<SlotRequirement> result = new ArrayList<>();
            for (JsonNode slot : slots) {
                String key = slot.path("key").asText("").trim();
                if (key.isBlank()) {
                    continue;
                }
                result.add(new SlotRequirement(
                        key,
                        slot.path("required").asBoolean(false),
                        slot.path("defaultable").asBoolean(false),
                        requiredLevel(slot),
                        parseAliases(slot)));
            }
            return List.copyOf(result);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    /** 解析槽位别名。 */
    private List<String> parseAliases(JsonNode slot) {
        JsonNode aliases = slot.path("aliases");
        Set<String> result = new LinkedHashSet<>();
        String key = slot.path("key").asText("").trim();
        String label = slot.path("label").asText("").trim();
        if (!key.isBlank()) {
            result.add(key);
        }
        if (!label.isBlank()) {
            result.add(label);
        }
        if (!aliases.isArray()) {
            result.addAll(semanticAliases(key, label, List.of()));
            return List.copyOf(result);
        }
        List<String> rawAliases = new ArrayList<>();
        for (JsonNode alias : aliases) {
            String value = alias.asText("").trim();
            if (!value.isBlank()) {
                result.add(value);
                rawAliases.add(value);
            }
        }
        result.addAll(semanticAliases(key, label, rawAliases));
        return List.copyOf(result);
    }

    /**
     * 为模型自由生成的合同槽位补充稳定语义别名。
     *
     * <p>不同模型可能把“使用场景”写成 context、scenario、purpose，把“风格”写成 tone、
     * voice 或 style。这里把这些表达折叠到系统稳定槽位，避免合同合法但后续事实无法命中的问题。</p>
     */
    private List<String> semanticAliases(
            String key,
            String label,
            List<String> aliases) {
        String text = normalize(key + " " + label + " "
                + String.join(" ", aliases == null ? List.of() : aliases));
        Set<String> result = new LinkedHashSet<>();
        addSemanticAliases(
                result,
                text,
                Slot.NAME,
                "name", "username", "fullname", "姓名", "名字", "称呼");
        addSemanticAliases(
                result,
                text,
                Slot.PURPOSE,
                "purpose", "usecase", "usage", "scenario", "context",
                "occasion", "target", "用途", "场景", "场合", "目的");
        addSemanticAliases(
                result,
                text,
                Slot.STYLE,
                "style", "tone", "voice", "语气", "风格", "表达方式");
        addSemanticAliases(
                result,
                text,
                Slot.LENGTH,
                "length", "wordcount", "size", "字数", "长度", "篇幅");
        addSemanticAliases(
                result,
                text,
                Slot.BACKGROUND,
                "identity", "profile", "background", "role", "occupation",
                "profession", "experience", "身份", "背景", "职业", "岗位",
                "经验");
        return List.copyOf(result);
    }

    /**
     * 如果模型合同槽位命中某个稳定语义，则补充该稳定槽位的全部事实别名。
     */
    private void addSemanticAliases(
            Set<String> result,
            String normalizedText,
            Slot slot,
            String... hints) {
        if (!containsAny(normalizedText, hints)) {
            return;
        }
        result.add(slot.key());
        result.addAll(SLOT_ALIASES.getOrDefault(slot, List.of()));
        if (slot == Slot.BACKGROUND) {
            result.addAll(SLOT_ALIASES.getOrDefault(Slot.ROLE, List.of()));
            result.addAll(SLOT_ALIASES.getOrDefault(Slot.EXPERIENCE, List.of()));
        }
    }

    /** 将模型缺失字段映射到合同槽位。 */
    private SlotRequirement contractRequirementForField(
            String normalizedField,
            List<SlotRequirement> contractSlots) {
        for (SlotRequirement slot : contractSlots) {
            if (normalize(slot.key()).equals(normalizedField)) {
                return slot;
            }
            boolean aliasMatched = slot.aliases().stream()
                    .map(this::normalize)
                    .anyMatch(alias -> alias.equals(normalizedField));
            if (aliasMatched) {
                return slot;
            }
        }
        return null;
    }

    /** 将模型字段名映射到稳定槽位。 */
    private Slot slotForField(String field) {
        for (Map.Entry<Slot, List<String>> entry : SLOT_ALIASES.entrySet()) {
            if (entry.getValue().stream()
                    .map(this::normalize)
                    .anyMatch(alias -> alias.equals(field))) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** 判断事实 Map 是否有指定字段的非空值。 */
    private boolean hasExactFact(
            String field,
            Map<String, String> facts) {
        String normalizedField = normalize(field);
        return facts.entrySet().stream()
                .anyMatch(entry -> normalize(entry.getKey())
                        .equals(normalizedField)
                        && entry.getValue() != null
                        && !entry.getValue().isBlank());
    }

    /**
     * 判断缺失槽位是否可由用户明确的“按默认处理”授权补齐。
     *
     * <p>默认授权只对软阻塞或显式 defaultable 的字段生效；硬阻塞字段仍必须由用户
     * 明确提供。这保证个人介绍这类低风险生成任务可以自然推进，同时不会放松工具调用、
     * 接口写入或授权类任务的关键参数要求。</p>
     */
    private boolean slotSatisfiedByDefaultConsent(
            SlotRequirement slot,
            boolean acceptedDefaults,
            boolean generationDefaultsAllowed) {
        return acceptedDefaults
                && ((slot.defaultable()
                        && slot.requiredLevel() != RequirementLevel.BLOCKING)
                        || (generationDefaultsAllowed
                        && isGenerationDefaultableSlot(slot)));
    }

    /**
     * 判断当前澄清是否属于低风险文本生成场景。
     */
    private boolean looksLikeGenerationClarification(
            String question,
            String blockingSummary,
            List<SlotRequirement> contractSlots) {
        String contractText = contractSlots.stream()
                .map(slot -> slot.key() + " " + String.join(" ", slot.aliases()))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        String text = normalize(question + " " + blockingSummary + " "
                + contractText);
        return containsAny(
                text,
                "简历",
                "个人介绍",
                "自我介绍",
                "介绍",
                "文案",
                "resume",
                "cv",
                "profile",
                "introduction",
                "copywriting");
    }

    /**
     * 判断硬合同槽位是否可在低风险生成场景由用户默认授权兜底。
     */
    private boolean isGenerationDefaultableSlot(SlotRequirement slot) {
        String text = normalize(slot.key() + " " + String.join(" ", slot.aliases()));
        if (containsAny(
                text,
                "name",
                "username",
                "fullname",
                "姓名",
                "名字",
                "称呼")) {
            return false;
        }
        return containsAny(
                text,
                "purpose",
                "usecase",
                "scenario",
                "context",
                "用途",
                "场景",
                "目的",
                "background",
                "identity",
                "role",
                "occupation",
                "profession",
                "背景",
                "身份",
                "职业",
                "岗位",
                "经验",
                "experience",
                "workexperience",
                "work_experience",
                "工作经历",
                "工作经验",
                "education",
                "school",
                "major",
                "degree",
                "教育背景",
                "学历",
                "学校",
                "专业",
                "contact",
                "phone",
                "mobile",
                "email",
                "联系方式",
                "电话",
                "邮箱",
                "targetposition",
                "target_position",
                "jobtarget",
                "job_target",
                "求职意向",
                "目标职位",
                "应聘职位",
                "skills",
                "技能",
                "证书",
                "style",
                "tone",
                "风格",
                "语气",
                "length",
                "wordcount",
                "长度",
                "字数");
    }

    /** 判断布尔型事实是否表达了用户确认。 */
    private boolean hasTruthyValue(
            Map<String, String> facts,
            String field) {
        String normalizedField = normalize(field);
        return facts.entrySet().stream()
                .anyMatch(entry -> normalize(entry.getKey())
                        .equals(normalizedField)
                        && truthy(entry.getValue()));
    }

    /** 识别常见布尔确认值。 */
    private boolean truthy(String value) {
        String normalized = normalize(value);
        return List.of("true", "yes", "y", "1", "是", "对", "默认", "无")
                .contains(normalized);
    }

    /** 判断模型缺失字段是否是可由“无特别要求”覆盖的要求类字段。 */
    private boolean isRequirementField(String field) {
        return REQUIREMENT_ALIASES.stream()
                .map(this::normalize)
                .anyMatch(alias -> alias.equals(field));
    }

    /** 判断字段在当前默认合同里是否不应阻塞恢复。 */
    private boolean isOptionalField(String field) {
        return isRequirementField(field)
                || "targetaudience".equals(field)
                || "outputformat".equals(field)
                || "format".equals(field);
    }

    /** 生成系统审计摘要。 */
    private String summary(
            String modelSummary,
            Map<String, String> facts,
            List<String> missing) {
        String prefix = modelSummary == null || modelSummary.isBlank()
                ? "结构化澄清抽取完成"
                : modelSummary.trim();
        String factKeys = facts.isEmpty()
                ? "无事实"
                : String.join(",", facts.keySet());
        String missingText = missing.isEmpty()
                ? "无缺失"
                : String.join(",", new LinkedHashSet<>(missing));
        return "%s；facts=%s；missing=%s".formatted(
                prefix,
                factKeys,
                missingText);
    }

    /** 判断文本是否包含任一关键词。 */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(normalize(keyword))) {
                return true;
            }
        }
        return false;
    }

    /** 归一化字符串供字段和中文关键词比较。 */
    private String normalize(String value) {
        return value == null
                ? ""
                : value.replaceAll("\\s+", "")
                        .trim()
                        .toLowerCase(Locale.ROOT);
    }

    /** 澄清合同中的通用稳定槽位。 */
    private enum Slot {
        NAME("name"),
        BACKGROUND("background"),
        ROLE("role"),
        EXPERIENCE("experience"),
        PURPOSE("purpose"),
        STYLE("style"),
        LENGTH("length");

        private final String key;

        /** 创建槽位。 */
        Slot(String key) {
            this.key = key;
        }

        /** @return 对外审计使用的稳定字段名 */
        private String key() {
            return key;
        }
    }

    /** 解析合同中的阻塞等级。 */
    private RequirementLevel requiredLevel(JsonNode slot) {
        String raw = slot.path("requiredLevel").asText("").trim();
        if (!raw.isBlank()) {
            try {
                return RequirementLevel.valueOf(
                        raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // 非法等级按旧合同字段兜底，避免模型拼写错误直接破坏恢复流程。
            }
        }
        boolean required = slot.path("required").asBoolean(false);
        boolean defaultable = slot.path("defaultable").asBoolean(false);
        if (!required) {
            return RequirementLevel.OPTIONAL;
        }
        return defaultable
                ? RequirementLevel.SOFT
                : RequirementLevel.BLOCKING;
    }

    /** 澄清槽位对恢复执行的阻塞强度。 */
    private enum RequirementLevel {
        /** 缺失时必须继续澄清，不能由用户笼统默认授权绕过。 */
        BLOCKING,
        /** 缺失时建议澄清；用户明确接受默认值后可以恢复。 */
        SOFT,
        /** 只作为质量偏好，不阻塞恢复。 */
        OPTIONAL
    }

    /** 合同定义的槽位要求。 */
    private record SlotRequirement(
            String key,
            boolean required,
            boolean defaultable,
            RequirementLevel requiredLevel,
            List<String> aliases) {

        /** 复制别名集合。 */
        private SlotRequirement {
            requiredLevel = requiredLevel == null
                    ? RequirementLevel.BLOCKING
                    : requiredLevel;
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }

        /** @return 缺失该槽位时是否应阻塞恢复 */
        private boolean blocksResume() {
            return required && requiredLevel != RequirementLevel.OPTIONAL;
        }
    }
}
