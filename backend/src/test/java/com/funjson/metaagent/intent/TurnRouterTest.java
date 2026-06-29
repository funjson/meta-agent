package com.funjson.metaagent.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.funjson.metaagent.intent.application.IntentRecognitionService;
import com.funjson.metaagent.intent.application.PendingInteractionRouter;
import com.funjson.metaagent.intent.application.TurnRouter;
import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.intent.domain.IntentRiskLevel;
import com.funjson.metaagent.intent.domain.IntentType;
import com.funjson.metaagent.intent.domain.PendingInteractionCandidate;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import com.funjson.metaagent.intent.domain.PendingInteractionRoute;
import com.funjson.metaagent.intent.domain.PendingInteractionRouteType;
import com.funjson.metaagent.intent.domain.TurnActionType;
import com.funjson.metaagent.intent.domain.TurnRoutingPlan;
import com.funjson.metaagent.intent.domain.TurnRoutingRequest;
import org.junit.jupiter.api.Test;

/**
 * Verifies the thin TurnRouter composition above pending interaction and
 * single-intent recognition.
 */
class TurnRouterTest {

    @Test
    void buildsMixedPlanForPendingAnswerAndAdditionalJob() {
        PendingInteractionRouter pendingRouter = mock(PendingInteractionRouter.class);
        IntentRecognitionService intentService = mock(IntentRecognitionService.class);
        UUID targetId = UUID.randomUUID();
        when(pendingRouter.route(any())).thenReturn(new PendingInteractionRoute(
                PendingInteractionRouteType.ANSWER_CLARIFICATION,
                targetId,
                0.94,
                "",
                new PendingInteractionFacts(
                        Map.of("name", "冯建松"),
                        List.of(),
                        "抽取到姓名"),
                "",
                "命中等待澄清"));
        when(intentService.recognize(any())).thenReturn(new IntentRecognition(
                IntentType.CREATE_JOB,
                0.91,
                "TEST",
                "写朋友圈版本个人介绍",
                "用户还要求新增朋友圈版本",
                List.of("personal-introduction"),
                false,
                false,
                IntentRiskLevel.LOW,
                List.of("social-post")));
        TurnRouter router = new TurnRouter(pendingRouter, intentService);

        TurnRoutingPlan plan = router.route(new TurnRoutingRequest(
                "我叫冯建松，其他随意。然后再帮我写一个朋友圈版本",
                "context",
                null,
                List.of(candidate(targetId)),
                true));

        assertThat(plan.mixed()).isTrue();
        assertThat(plan.actions())
                .extracting(action -> action.actionType())
                .containsExactly(
                        TurnActionType.ANSWER_PENDING,
                        TurnActionType.CREATE_JOB);
        assertThat(plan.actions().getFirst().answerText())
                .contains("我叫冯建松")
                .doesNotContain("朋友圈版本");
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
