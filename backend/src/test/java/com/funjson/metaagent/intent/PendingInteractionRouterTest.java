package com.funjson.metaagent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.intent.application.PendingInteractionMatcher;
import com.funjson.metaagent.intent.application.PendingInteractionRouter;
import com.funjson.metaagent.intent.application.port.out.ModelPendingInteractionRouterPort;
import com.funjson.metaagent.intent.domain.PendingInteractionCandidate;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import com.funjson.metaagent.intent.domain.PendingInteractionRoute;
import com.funjson.metaagent.intent.domain.PendingInteractionRouteType;
import com.funjson.metaagent.intent.domain.PendingInteractionRoutingRequest;
import org.junit.jupiter.api.Test;

/**
 * 验证等待交互 Router 的模型优先、合同校验和降级抽取。
 */
class PendingInteractionRouterTest {

    @Test
    void usesTrustedModelRouteWhenTargetIsOpen() {
        PendingInteractionCandidate candidate = candidate(
                "请补充姓名、用途和风格");
        PendingInteractionRouter router = routerWithModel(
                new PendingInteractionRoute(
                        PendingInteractionRouteType.ANSWER_CLARIFICATION,
                        candidate.id(),
                        0.92,
                        "我叫冯建松",
                        new PendingInteractionFacts(
                                Map.of("name", "冯建松"),
                                List.of("purpose"),
                                "用户补充姓名"),
                        "",
                        "模型命中唯一等待项"));

        PendingInteractionRoute route = router.route(request(
                "我叫冯建松",
                List.of(candidate),
                true));

        assertThat(route.routeType())
                .isEqualTo(PendingInteractionRouteType.ANSWER_CLARIFICATION);
        assertThat(route.targetId()).isEqualTo(candidate.id());
        assertThat(route.facts().facts()).containsEntry("name", "冯建松");
    }

    @Test
    void fallsBackWhenModelTargetIsNotOpen() {
        PendingInteractionCandidate candidate = candidate(
                "请补充姓名、用途和风格");
        PendingInteractionRouter router = routerWithModel(
                new PendingInteractionRoute(
                        PendingInteractionRouteType.ANSWER_CLARIFICATION,
                        UUID.randomUUID(),
                        0.99,
                        "我叫冯建松",
                        PendingInteractionFacts.empty(),
                        "",
                        "模型返回了陈旧目标"));

        PendingInteractionRoute route = router.route(request(
                "我叫冯建松",
                List.of(candidate),
                true));

        assertThat(route.routeType())
                .isEqualTo(PendingInteractionRouteType.ANSWER_CLARIFICATION);
        assertThat(route.targetId()).isEqualTo(candidate.id());
    }

    @Test
    void fallbackExtractsExplicitNameForSingleCandidate() {
        PendingInteractionRouter router = routerWithoutModel();
        PendingInteractionCandidate candidate = candidate(
                "请补充姓名、用途和风格");

        PendingInteractionRoute route = router.route(request(
                "我叫冯建松",
                List.of(candidate),
                false));

        assertThat(route.routeType())
                .isEqualTo(PendingInteractionRouteType.ANSWER_CLARIFICATION);
        assertThat(route.facts().facts()).containsEntry("name", "冯建松");
    }

    @Test
    void fallbackExtractsCommonProfileFieldsForClarificationAnswer() {
        PendingInteractionRouter router = routerWithoutModel();
        PendingInteractionCandidate candidate = candidate(
                "请补充岗位或行业、背景经验、风格、长度和特别要求");

        PendingInteractionRoute route = router.route(request(
                "软件开发 10年经验 正式一些 100字左右吧 没有其他特别要求",
                List.of(candidate),
                false));

        assertThat(route.routeType())
                .isEqualTo(PendingInteractionRouteType.ANSWER_CLARIFICATION);
        assertThat(route.facts().facts())
                .containsEntry("role", "软件开发")
                .containsEntry("experience", "10年经验")
                .containsEntry("style", "正式")
                .containsEntry("length", "100字")
                .containsEntry("noSpecialRequirements", "true");
    }

    @Test
    void routesRequirementHelpWithoutBindingAsAnswer() {
        PendingInteractionRouter router = routerWithoutModel();
        PendingInteractionCandidate candidate = candidate(
                "请补充姓名、用途和风格");

        PendingInteractionRoute route = router.route(request(
                "你希望我补充哪些内容",
                List.of(candidate),
                false));

        assertThat(route.routeType())
                .isEqualTo(PendingInteractionRouteType.EXPLAIN_PENDING_REQUIREMENTS);
        assertThat(route.targetId()).isEqualTo(candidate.id());
    }

    /**
     * 创建带固定模型返回的 Router。
     *
     * @param route 模型返回
     * @return Router
     */
    private PendingInteractionRouter routerWithModel(
            PendingInteractionRoute route) {
        ModelPendingInteractionRouterPort modelRouter =
                request -> Optional.of(route);
        return new PendingInteractionRouter(
                new PendingInteractionMatcher(),
                modelRouter);
    }

    /** @return 不调用模型的 Router */
    private PendingInteractionRouter routerWithoutModel() {
        return new PendingInteractionRouter(
                new PendingInteractionMatcher(),
                request -> Optional.empty());
    }

    /**
     * 创建路由请求。
     *
     * @param userMessage 用户消息
     * @param candidates 等待候选
     * @param modelAllowed 是否允许模型
     * @return 路由请求
     */
    private PendingInteractionRoutingRequest request(
            String userMessage,
            List<PendingInteractionCandidate> candidates,
            boolean modelAllowed) {
        return new PendingInteractionRoutingRequest(
                userMessage,
                "conversation context",
                candidates,
                modelAllowed);
    }

    /**
     * 创建等待候选。
     *
     * @param question 澄清问题
     * @return 候选
     */
    private PendingInteractionCandidate candidate(String question) {
        return new PendingInteractionCandidate(
                UUID.randomUUID(),
                "CLARIFICATION",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                question,
                question);
    }
}
