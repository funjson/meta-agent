package com.funjson.metaagent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.intent.application.IntentRecognitionService;
import com.funjson.metaagent.intent.application.PendingInteractionRouter;
import com.funjson.metaagent.intent.application.RuleTurnFallbackRouter;
import com.funjson.metaagent.intent.application.TurnPlanValidator;
import com.funjson.metaagent.intent.application.TurnUnderstandingService;
import com.funjson.metaagent.intent.application.port.out.ModelTurnUnderstandingPort;
import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.intent.domain.IntentRewrite;
import com.funjson.metaagent.intent.domain.IntentRiskLevel;
import com.funjson.metaagent.intent.domain.IntentType;
import com.funjson.metaagent.intent.domain.PendingInteractionCandidate;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import com.funjson.metaagent.intent.domain.TurnAction;
import com.funjson.metaagent.intent.domain.TurnActionType;
import com.funjson.metaagent.intent.domain.TurnIntentGraph;
import com.funjson.metaagent.intent.domain.TurnIntentNode;
import com.funjson.metaagent.intent.domain.TurnIntentNodeKind;
import com.funjson.metaagent.intent.domain.TurnRoutingRequest;
import com.funjson.metaagent.intent.domain.TurnTaskType;
import com.funjson.metaagent.intent.domain.TurnUnderstanding;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests for graph-based mixed intent and task-level intent rewrite.
 */
class TurnUnderstandingSmokeTest {

    @Test
    void supportsClarificationAnswerPlusNewResearchTaskInOneTurn() {
        UUID clarificationId = UUID.randomUUID();
        ModelTurnUnderstandingPort model = request -> Optional.of(
                new TurnUnderstanding(
                        new TurnIntentGraph(
                                List.of(
                                        pendingNode(clarificationId),
                                        deepResearchNode()),
                                List.of(),
                                "先恢复澄清，再创建研究任务"),
                        "先恢复澄清，再创建研究任务"));
        TurnUnderstandingService service = new TurnUnderstandingService(
                model,
                new RuleTurnFallbackRouter(
                        ignoredPendingRouter(),
                        ignoredIntentService()),
                new TurnPlanValidator());

        var plan = service.route(new TurnRoutingRequest(
                "我叫冯建松，其他默认即可，再 deep research 一个 Java 安全架构",
                "context",
                null,
                List.of(candidate(clarificationId)),
                true));

        assertThat(plan.actions())
                .extracting(TurnAction::actionType)
                .containsExactly(
                        TurnActionType.ANSWER_PENDING,
                        TurnActionType.CREATE_JOB);
        assertThat(plan.graph().nodes())
                .extracting(TurnIntentNode::taskType)
                .containsExactly(
                        TurnTaskType.CLARIFICATION_ANSWER,
                        TurnTaskType.DEEP_RESEARCH);
        assertThat(plan.actions().get(1).jobRequestText(""))
                .contains("Java 应用系统安全架构设计")
                .doesNotContain("site:");
        assertThat(plan.actions().get(1).recognition().labels())
                .contains("research-depth:deep-research");
    }

    /**
     * Creates the pending-answer node.
     */
    private TurnIntentNode pendingNode(UUID clarificationId) {
        return new TurnIntentNode(
                "node-1",
                TurnIntentNodeKind.ANSWER_PENDING,
                TurnTaskType.CLARIFICATION_ANSWER,
                "我叫冯建松，其他默认即可",
                "",
                List.of("clarification-answer"),
                TurnAction.answerPending(
                        clarificationId,
                        "我叫冯建松，其他默认即可",
                        new PendingInteractionFacts(
                                Map.of(
                                        "name",
                                        "冯建松",
                                        "userAcceptedDefaults",
                                        "true"),
                                List.of(),
                                "用户补充姓名并接受默认值"),
                        "恢复等待澄清"));
    }

    /**
     * Creates the deep research node.
     */
    private TurnIntentNode deepResearchNode() {
        IntentRecognition recognition = new IntentRecognition(
                IntentType.CREATE_JOB,
                0.93,
                "TEST",
                "调研 Java 应用系统安全架构设计",
                "模型将口语化请求规范为 deep research 任务。",
                List.of("输出结构化报告"),
                false,
                true,
                IntentRiskLevel.MEDIUM,
                List.of(
                        "needs-web",
                        "needs-citation",
                        "research-depth:deep-research"));
        return new TurnIntentNode(
                "node-2",
                TurnIntentNodeKind.NEW_JOB,
                TurnTaskType.DEEP_RESEARCH,
                "deep research 一个 Java 安全架构",
                "调研 Java 应用系统安全架构设计，覆盖认证、授权、数据安全、审计、供应链安全和落地建议，输出结构化报告。",
                recognition.labels(),
                TurnAction.createJob(
                        recognition,
                        "deep research 一个 Java 安全架构",
                        "deep research 一个 Java 安全架构",
                        "调研 Java 应用系统安全架构设计，覆盖认证、授权、数据安全、审计、供应链安全和落地建议，输出结构化报告。",
                        new IntentRewrite(
                                true,
                                "把口语化研究请求规范为清晰研究目标",
                                List.of("Java", "安全架构"))));
    }

    /**
     * Creates a pending candidate.
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
                "缺少姓名",
                "{}");
    }

    /**
     * Creates an unused pending router for model-success smoke tests.
     */
    private PendingInteractionRouter ignoredPendingRouter() {
        return org.mockito.Mockito.mock(PendingInteractionRouter.class);
    }

    /**
     * Creates an unused intent service for model-success smoke tests.
     */
    private IntentRecognitionService ignoredIntentService() {
        return org.mockito.Mockito.mock(IntentRecognitionService.class);
    }
}
