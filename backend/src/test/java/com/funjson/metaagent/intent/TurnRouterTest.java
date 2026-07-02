package com.funjson.metaagent.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.intent.application.IntentRecognitionService;
import com.funjson.metaagent.intent.application.PendingInteractionRouter;
import com.funjson.metaagent.intent.application.RuleTurnFallbackRouter;
import com.funjson.metaagent.intent.application.TurnPlanValidator;
import com.funjson.metaagent.intent.application.TurnRouter;
import com.funjson.metaagent.intent.application.TurnUnderstandingService;
import com.funjson.metaagent.intent.application.port.out.ModelTurnUnderstandingPort;
import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.intent.domain.IntentRewrite;
import com.funjson.metaagent.intent.domain.IntentRiskLevel;
import com.funjson.metaagent.intent.domain.IntentType;
import com.funjson.metaagent.intent.domain.PendingInteractionCandidate;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import com.funjson.metaagent.intent.domain.PendingInteractionRoute;
import com.funjson.metaagent.intent.domain.PendingInteractionRouteType;
import com.funjson.metaagent.intent.domain.TurnAction;
import com.funjson.metaagent.intent.domain.TurnActionType;
import com.funjson.metaagent.intent.domain.TurnDependencyType;
import com.funjson.metaagent.intent.domain.TurnIntentEdge;
import com.funjson.metaagent.intent.domain.TurnIntentGraph;
import com.funjson.metaagent.intent.domain.TurnIntentNode;
import com.funjson.metaagent.intent.domain.TurnIntentNodeKind;
import com.funjson.metaagent.intent.domain.TurnRoutingPlan;
import com.funjson.metaagent.intent.domain.TurnRoutingRequest;
import com.funjson.metaagent.intent.domain.TurnTaskType;
import com.funjson.metaagent.intent.domain.TurnUnderstanding;
import org.junit.jupiter.api.Test;

/**
 * Verifies the formal TurnRouter facade and graph-based turn understanding.
 */
class TurnRouterTest {

    @Test
    void routesModelGraphWithPendingAnswerAndIndependentWeatherJob() {
        UUID targetId = UUID.randomUUID();
        TurnRouter router = routerWithModel(new TurnUnderstanding(
                new TurnIntentGraph(
                        List.of(
                                pendingNode(
                                        "node-1",
                                        targetId,
                                        "我叫冯建松",
                                        Map.of("name", "冯建松")),
                                createJobNode(
                                        "node-2",
                                        TurnTaskType.WEATHER_QUERY,
                                        "查一下北京今天的天气",
                                        "查询北京今天的天气，并给出简洁中文回答",
                                        List.of("weather", "needs-fresh-info"))),
                        List.of(),
                        "用户同时补充姓名并提出天气查询"),
                "用户同时补充姓名并提出天气查询"));

        TurnRoutingPlan plan = router.route(new TurnRoutingRequest(
                "我叫冯建松，哦对了再帮我查一下北京今天的天气",
                "context",
                null,
                List.of(candidate(targetId)),
                true));

        assertThat(plan.mixed()).isTrue();
        assertThat(plan.actions())
                .extracting(TurnAction::actionType)
                .containsExactly(
                        TurnActionType.ANSWER_PENDING,
                        TurnActionType.CREATE_JOB);
        assertThat(plan.graph().nodes())
                .extracting(TurnIntentNode::taskType)
                .containsExactly(
                        TurnTaskType.CLARIFICATION_ANSWER,
                        TurnTaskType.WEATHER_QUERY);
        assertThat(plan.actions().get(1).recognition().labels())
                .contains("weather", "needs-fresh-info");
    }

    @Test
    void preservesDependencyEdgesForResultDependentTasks() {
        TurnRouter router = routerWithModel(new TurnUnderstanding(
                new TurnIntentGraph(
                        List.of(
                                createJobNode(
                                        "node-1",
                                        TurnTaskType.WEATHER_QUERY,
                                        "查一下北京今天的天气",
                                        "查询北京今天的天气",
                                        List.of("weather", "needs-fresh-info")),
                                createJobNode(
                                        "node-2",
                                        TurnTaskType.TEXT_GENERATION,
                                        "根据天气给我出门建议",
                                        "根据天气结果生成出门建议",
                                        List.of("text-generation"))),
                        List.of(new TurnIntentEdge(
                                "node-1",
                                "node-2",
                                TurnDependencyType.DEPENDS_ON_RESULT,
                                "出门建议需要天气结果")),
                        "天气查询结果供出门建议使用"),
                "天气查询结果供出门建议使用"));

        TurnRoutingPlan plan = router.route(new TurnRoutingRequest(
                "查一下北京今天的天气，然后根据天气给我出门建议",
                "context",
                null,
                List.of(),
                true));

        assertThat(plan.graph().edges()).hasSize(1);
        assertThat(plan.graph().edges().getFirst().relationType())
                .isEqualTo(TurnDependencyType.DEPENDS_ON_RESULT);
    }

    @Test
    void fallsBackToSinglePendingAnswerWhenModelIsUnavailable() {
        PendingInteractionRouter pendingRouter =
                mock(PendingInteractionRouter.class);
        IntentRecognitionService intentService =
                mock(IntentRecognitionService.class);
        UUID targetId = UUID.randomUUID();
        when(pendingRouter.route(any())).thenReturn(new PendingInteractionRoute(
                PendingInteractionRouteType.ANSWER_CLARIFICATION,
                targetId,
                0.94,
                "我叫冯建松",
                new PendingInteractionFacts(
                        Map.of("name", "冯建松"),
                        List.of(),
                        "抽取到姓名"),
                "",
                "命中等待澄清"));
        TurnRouter router = router(
                request -> Optional.empty(),
                pendingRouter,
                intentService);

        TurnRoutingPlan plan = router.route(new TurnRoutingRequest(
                "我叫冯建松",
                "context",
                null,
                List.of(candidate(targetId)),
                true));

        assertThat(plan.mixed()).isFalse();
        assertThat(plan.actions().getFirst().actionType())
                .isEqualTo(TurnActionType.ANSWER_PENDING);
        assertThat(plan.actions().getFirst().answerText())
                .isEqualTo("我叫冯建松");
    }

    @Test
    void rejectsInvalidToolQueryRewriteAtIntentLayerAndFallsBack() {
        PendingInteractionRouter pendingRouter =
                mock(PendingInteractionRouter.class);
        IntentRecognitionService intentService =
                mock(IntentRecognitionService.class);
        when(pendingRouter.route(any())).thenReturn(
                PendingInteractionRoute.newIntent("无等待项"));
        when(intentService.recognize(any())).thenReturn(
                createJobRecognition(
                        "研究 Java 系统安全架构",
                        List.of("needs-web")));
        TurnRouter router = router(
                request -> Optional.of(new TurnUnderstanding(
                        List.of(TurnAction.createJob(
                                createJobRecognition(
                                        "Java 安全架构",
                                        List.of("needs-web")),
                                "研究 Java 系统安全架构",
                                "研究 Java 系统安全架构",
                                "site:owasp.org Java security architecture",
                                new IntentRewrite(
                                        true,
                                        "错误地产生了搜索查询",
                                        List.of("Java")))),
                        "错误模型计划")),
                pendingRouter,
                intentService);

        TurnRoutingPlan plan = router.route(new TurnRoutingRequest(
                "研究 Java 系统安全架构",
                "context",
                null,
                List.of(),
                true));

        assertThat(plan.actions()).hasSize(1);
        assertThat(plan.actions().getFirst().canonicalGoal())
                .isEqualTo("研究 Java 系统安全架构");
    }

    @Test
    void rejectsCyclicDependencyGraphAndFallsBack() {
        PendingInteractionRouter pendingRouter =
                mock(PendingInteractionRouter.class);
        IntentRecognitionService intentService =
                mock(IntentRecognitionService.class);
        when(pendingRouter.route(any())).thenReturn(
                PendingInteractionRoute.newIntent("无等待项"));
        when(intentService.recognize(any())).thenReturn(
                createJobRecognition("查询北京天气", List.of("weather")));
        TurnRouter router = router(
                request -> Optional.of(new TurnUnderstanding(
                        new TurnIntentGraph(
                                List.of(
                                        createJobNode(
                                                "node-1",
                                                TurnTaskType.WEATHER_QUERY,
                                                "查天气",
                                                "查询北京天气",
                                                List.of("weather")),
                                        createJobNode(
                                                "node-2",
                                                TurnTaskType.TEXT_GENERATION,
                                                "写建议",
                                                "生成出门建议",
                                                List.of("text-generation"))),
                                List.of(
                                        new TurnIntentEdge(
                                                "node-1",
                                                "node-2",
                                                TurnDependencyType
                                                        .DEPENDS_ON_RESULT,
                                                "天气供建议使用"),
                                        new TurnIntentEdge(
                                                "node-2",
                                                "node-1",
                                                TurnDependencyType
                                                        .DEPENDS_ON_RESULT,
                                                "错误反向依赖")),
                                "错误循环图"),
                        "错误循环图")),
                pendingRouter,
                intentService);

        TurnRoutingPlan plan = router.route(new TurnRoutingRequest(
                "查北京天气然后写建议",
                "context",
                null,
                List.of(),
                true));

        assertThat(plan.graph().nodes()).hasSize(1);
        assertThat(plan.actions().getFirst().jobRequestText(""))
                .isEqualTo("查询北京天气");
    }

    /**
     * Creates a router whose model port always returns the given plan.
     */
    private TurnRouter routerWithModel(TurnUnderstanding understanding) {
        return router(
                request -> Optional.of(understanding),
                mock(PendingInteractionRouter.class),
                mock(IntentRecognitionService.class));
    }

    /**
     * Creates a router with explicit collaborators.
     */
    private TurnRouter router(
            ModelTurnUnderstandingPort modelPort,
            PendingInteractionRouter pendingRouter,
            IntentRecognitionService intentService) {
        return new TurnRouter(new TurnUnderstandingService(
                modelPort,
                new RuleTurnFallbackRouter(
                        pendingRouter,
                        intentService),
                new TurnPlanValidator()));
    }

    /**
     * Creates a pending-answer node for tests.
     */
    private TurnIntentNode pendingNode(
            String nodeId,
            UUID targetId,
            String answer,
            Map<String, String> facts) {
        return new TurnIntentNode(
                nodeId,
                TurnIntentNodeKind.ANSWER_PENDING,
                TurnTaskType.CLARIFICATION_ANSWER,
                answer,
                "",
                List.of("clarification-answer"),
                TurnAction.answerPending(
                        targetId,
                        answer,
                        new PendingInteractionFacts(
                                facts,
                                List.of(),
                                "抽取到澄清回答"),
                        "回答等待澄清"));
    }

    /**
     * Creates a create-job graph node for tests.
     */
    private TurnIntentNode createJobNode(
            String nodeId,
            TurnTaskType taskType,
            String sourceSpan,
            String canonicalGoal,
            List<String> labels) {
        return new TurnIntentNode(
                nodeId,
                TurnIntentNodeKind.NEW_JOB,
                taskType,
                sourceSpan,
                canonicalGoal,
                labels,
                TurnAction.createJob(
                        createJobRecognition(canonicalGoal, labels),
                        sourceSpan,
                        sourceSpan,
                        canonicalGoal,
                        new IntentRewrite(
                                true,
                                "规范化节点目标",
                                List.of(sourceSpan))));
    }

    /**
     * Creates a CREATE_JOB recognition for tests.
     */
    private IntentRecognition createJobRecognition(
            String goalSummary,
            List<String> labels) {
        return new IntentRecognition(
                IntentType.CREATE_JOB,
                0.91,
                "TEST",
                goalSummary,
                "测试任务识别",
                List.of(),
                false,
                false,
                IntentRiskLevel.LOW,
                labels);
    }

    /**
     * Creates a pending candidate for tests.
     */
    private PendingInteractionCandidate candidate(UUID id) {
        return new PendingInteractionCandidate(
                id,
                "CLARIFICATION",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                "请补充姓名和用途",
                "缺少关键信息",
                "{}");
    }
}
