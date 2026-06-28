package com.funjson.metaagent.intent.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.funjson.metaagent.intent.application.port.out.ModelPendingInteractionRouterPort;
import com.funjson.metaagent.intent.domain.PendingInteractionCandidate;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import com.funjson.metaagent.intent.domain.PendingInteractionMatch;
import com.funjson.metaagent.intent.domain.PendingInteractionMatchType;
import com.funjson.metaagent.intent.domain.PendingInteractionRoute;
import com.funjson.metaagent.intent.domain.PendingInteractionRouteType;
import com.funjson.metaagent.intent.domain.PendingInteractionRoutingRequest;
import org.springframework.stereotype.Service;

/**
 * 统一路由用户消息与当前等待交互之间的关系。
 *
 * <p>该 Router 是 Control Kernel 面向 Pending Interaction 的正式入口。模型可用时由
 * 模型完成“目标选择 + 结构化抽取”；模型不可用或输出不可信时，退回到保守规则匹配，
 * 避免为了单个场景继续在 Control 层堆叠 if 分支。</p>
 */
@Service
public class PendingInteractionRouter {

    private static final Pattern NAME_PATTERN =
            Pattern.compile(".*(?:我叫|我的名字叫|姓名是|名字是)\\s*([^，。,.；;\\s]+).*");
    private static final Pattern PURPOSE_PATTERN =
            Pattern.compile(".*(?:用于|用途是|用来|使用场景是)\\s*([^。；;]+).*");
    private static final Pattern STYLE_PATTERN =
            Pattern.compile(".*(?:风格(?:是|要)?|语气(?:是|要)?)\\s*([^。；;]+).*");
    private static final Pattern LENGTH_PATTERN =
            Pattern.compile(".*?(\\d+\\s*(?:字|句|段|分钟|秒)).*");
    private static final Pattern ROLE_PATTERN =
            Pattern.compile(".*(?:我是|职业是|身份是|岗位是|角色是)\\s*([^，。,.；;]+).*");
    private static final Pattern EXPERIENCE_PATTERN =
            Pattern.compile(".*?(\\d+\\s*年(?:经验|经历|工作经验)?).*");

    private final PendingInteractionMatcher fallbackMatcher;
    private final ModelPendingInteractionRouterPort modelRouter;

    /**
     * 创建等待交互 Router。
     *
     * @param fallbackMatcher 保守规则匹配器
     * @param modelRouter 模型结构化路由端口
     */
    public PendingInteractionRouter(
            PendingInteractionMatcher fallbackMatcher,
            ModelPendingInteractionRouterPort modelRouter) {
        this.fallbackMatcher = fallbackMatcher;
        this.modelRouter = modelRouter;
    }

    /**
     * 识别当前用户消息是否应该恢复某个等待交互。
     *
     * @param request 路由请求
     * @return 经过目标校验后的路由结果
     */
    public PendingInteractionRoute route(PendingInteractionRoutingRequest request) {
        if (request.candidates().isEmpty()) {
            return PendingInteractionRoute.newIntent("当前没有等待交互候选。");
        }
        PendingInteractionFacts conservativeFacts =
                extractConservativeFacts(request.userMessage());
        PendingInteractionRoute explainRoute =
                explainRequirementsRoute(request);
        if (explainRoute != null) {
            return explainRoute;
        }
        PendingInteractionRoute fallbackRoute = fallbackRoute(
                request,
                conservativeFacts);
        Optional<PendingInteractionRoute> modelRoute =
                request.modelRoutingAllowed()
                        ? modelRouter.route(request).filter(
                                route -> isTrusted(route, request.candidates()))
                                .map(route -> mergeConservativeFacts(
                                        route,
                                        conservativeFacts))
                        : Optional.empty();
        if (modelRoute.isPresent()
                && modelRoute.get().routeType()
                == PendingInteractionRouteType.NEW_INTENT
                && fallbackRoute.targetsWaitingInteraction()) {
            return fallbackRoute;
        }
        return modelRoute.orElse(fallbackRoute);
    }

    /**
     * 校验模型返回的 targetId 是否仍属于当前快照。
     *
     * @param route 模型路由结果
     * @param candidates 当前候选
     * @return 可信时返回 {@code true}
     */
    private boolean isTrusted(
            PendingInteractionRoute route,
            List<PendingInteractionCandidate> candidates) {
        if (route.routeType()
                == PendingInteractionRouteType.EXPLAIN_PENDING_REQUIREMENTS) {
            return route.targetId() != null
                    && candidates.stream()
                            .map(PendingInteractionCandidate::id)
                            .anyMatch(route.targetId()::equals);
        }
        if (!route.targetsWaitingInteraction()) {
            return route.routeType() == PendingInteractionRouteType.NEW_INTENT
                    || route.routeType() == PendingInteractionRouteType.AMBIGUOUS
                    || route.routeType() == PendingInteractionRouteType.CONTROL_COMMAND;
        }
        if (route.routeType() == PendingInteractionRouteType.SELECT_PENDING_INTERACTION
                && route.answerText().isBlank()) {
            return false;
        }
        // 模型只能绑定本轮 ContextEnvelope 中仍然打开的等待项，避免误恢复陈旧目标。
        return candidates.stream()
                .map(PendingInteractionCandidate::id)
                .anyMatch(route.targetId()::equals);
    }

    /**
     * 模型不可用时的保守降级路线。
     *
     * @param request 路由请求
     * @return 降级路由结果
     */
    private PendingInteractionRoute fallbackRoute(
            PendingInteractionRoutingRequest request,
            PendingInteractionFacts facts) {
        PendingInteractionMatch match = fallbackMatcher.match(
                request.userMessage(),
                request.candidates());
        if (match.answeredClarification()) {
            return new PendingInteractionRoute(
                    PendingInteractionRouteType.ANSWER_CLARIFICATION,
                    match.targetId(),
                    match.confidence(),
                    request.userMessage(),
                    facts,
                    "",
                    "保守规则命中等待澄清：" + match.summary());
        }
        if (match.matchType() == PendingInteractionMatchType.AMBIGUOUS) {
            return new PendingInteractionRoute(
                    PendingInteractionRouteType.AMBIGUOUS,
                    null,
                    match.confidence(),
                    "",
                    facts,
                    "我看到了补充信息，但还不能确定要补给哪个等待中的任务。"
                            + "请说明要作用到哪个等待项，或明确说这是一个新任务。",
                    match.summary());
        }
        return PendingInteractionRoute.newIntent(match.summary());
    }

    /**
     * 用户问“我要补什么”时，解释当前等待合同，不恢复任务也不创建新 Job。
     *
     * @param request 路由请求
     * @return 解释路由；不匹配时返回 {@code null}
     */
    private PendingInteractionRoute explainRequirementsRoute(
            PendingInteractionRoutingRequest request) {
        String value = request.userMessage() == null
                ? ""
                : request.userMessage().replaceAll("\\s+", "")
                        .toLowerCase(Locale.ROOT);
        boolean asksRequirements = value.matches(".*(补充哪些|需要补充什么|要补什么|还缺什么|需要哪些信息|你需要什么|你希望我补充).*");
        if (!asksRequirements || request.candidates().size() != 1) {
            return null;
        }
        return new PendingInteractionRoute(
                PendingInteractionRouteType.EXPLAIN_PENDING_REQUIREMENTS,
                request.candidates().getFirst().id(),
                0.93,
                "",
                PendingInteractionFacts.empty(),
                "",
                "用户询问当前等待澄清需要补充哪些信息。");
    }

    /**
     * 提供一个极保守的本地事实抽取兜底。
     *
     * <p>正式抽取仍由模型 Prompt 完成；这里仅抽取显式字段，避免模型临时不可用时完全丢失
     * “我叫冯建松”这类高确定性事实。</p>
     *
     * @param userMessage 用户补充文本
     * @return 结构化事实
     */
    private PendingInteractionFacts extractConservativeFacts(String userMessage) {
        String value = userMessage == null ? "" : userMessage.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        Map<String, String> facts = new LinkedHashMap<>();
        putMatch(facts, "name", NAME_PATTERN.matcher(value));
        putMatch(facts, "purpose", PURPOSE_PATTERN.matcher(value));
        putMatch(facts, "style", STYLE_PATTERN.matcher(value));
        putMatch(facts, "length", LENGTH_PATTERN.matcher(value));
        putMatch(facts, "role", ROLE_PATTERN.matcher(value));
        putMatch(facts, "experience", EXPERIENCE_PATTERN.matcher(value));
        if (!facts.containsKey("purpose")
                && value.matches(".*(求职|面试|应聘|简历).*")) {
            facts.put("purpose", "求职/面试相关");
        }
        if (!facts.containsKey("style")
                && value.matches(".*(正式|专业|轻松|幽默|亲和|温暖|简洁).*")) {
            facts.put("style", matchedStyle(value));
        }
        if (!facts.containsKey("role")
                && lower.matches(".*(java|软件开发|后端|前端|全栈|产品|运营|设计|财务|市场).*")) {
            facts.put("role", matchedRole(value));
        }
        if (value.matches(".*(没有其他特别要求|无特别要求|没有特别要求|没什么亮点|没有亮点).*")) {
            facts.put("noSpecialRequirements", "true");
        }
        if (lower.contains("默认") || lower.contains("通用")
                || value.matches(".*(随意|都行|看着办|你决定|无所谓|随便|其他的随意|其他随意|没有了|没了|没有其他|不用补充|不补充了|先这样|就这样).*")) {
            facts.put("userAcceptedDefaults", "true");
        }
        return new PendingInteractionFacts(
                facts,
                List.of(),
                facts.isEmpty()
                        ? ""
                        : "本地兜底抽取到字段：" + String.join(",", facts.keySet()));
    }

    /**
     * 将模型抽取结果与本地高确定性字段合并。
     *
     * <p>模型结果优先；本地兜底只补齐姓名、年限、长度这类低歧义事实，避免
     * 模型临时漏抽导致澄清合同无法收口。</p>
     *
     * @param route 模型路由
     * @param conservativeFacts 本地兜底事实
     * @return 合并后的路由
     */
    private PendingInteractionRoute mergeConservativeFacts(
            PendingInteractionRoute route,
            PendingInteractionFacts conservativeFacts) {
        Map<String, String> mergedFacts = new LinkedHashMap<>(
                conservativeFacts.facts());
        mergedFacts.putAll(route.facts().facts());
        PendingInteractionFacts merged = new PendingInteractionFacts(
                mergedFacts,
                route.facts().missingFields(),
                route.facts().answerSummary().isBlank()
                        ? conservativeFacts.answerSummary()
                        : route.facts().answerSummary());
        return new PendingInteractionRoute(
                route.routeType(),
                route.targetId(),
                route.confidence(),
                route.answerText(),
                merged,
                route.userFacingMessage(),
                route.auditSummary());
    }

    /**
     * 从常见表达中提取风格关键词。
     */
    private String matchedStyle(String value) {
        for (String style : List.of("正式", "专业", "轻松", "幽默", "亲和", "温暖", "简洁")) {
            if (value.contains(style)) {
                return style;
            }
        }
        return "未指定风格";
    }

    /**
     * 从短句中提取岗位或方向关键词。
     */
    private String matchedRole(String value) {
        for (String role : List.of(
                "Java", "java", "软件开发", "后端", "前端", "全栈",
                "产品", "运营", "设计", "财务", "市场")) {
            if (value.contains(role)) {
                return role;
            }
        }
        return "未指定方向";
    }

    /**
     * 正则命中时写入非空字段。
     *
     * @param facts 事实 Map
     * @param key 字段名
     * @param matcher 正则匹配器
     */
    private void putMatch(
            Map<String, String> facts,
            String key,
            Matcher matcher) {
        if (matcher.matches()) {
            String value = matcher.group(1).trim();
            if (!value.isBlank()) {
                facts.put(key, value);
            }
        }
    }
}
