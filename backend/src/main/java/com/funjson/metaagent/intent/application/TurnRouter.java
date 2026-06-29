package com.funjson.metaagent.intent.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.intent.domain.IntentRecognitionRequest;
import com.funjson.metaagent.intent.domain.IntentType;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import com.funjson.metaagent.intent.domain.PendingInteractionRoute;
import com.funjson.metaagent.intent.domain.PendingInteractionRouteType;
import com.funjson.metaagent.intent.domain.PendingInteractionRoutingRequest;
import com.funjson.metaagent.intent.domain.TurnAction;
import com.funjson.metaagent.intent.domain.TurnActionType;
import com.funjson.metaagent.intent.domain.TurnRoutingPlan;
import com.funjson.metaagent.intent.domain.TurnRoutingRequest;
import org.springframework.stereotype.Service;

/**
 * Builds an ordered Control action plan for a user turn.
 *
 * <p>V0.1 intentionally keeps this router thin: it reuses the existing pending
 * interaction router and intent recognition pipeline, then combines their
 * outputs into a small multi-action plan. A dedicated model turn router can be
 * introduced later without changing the Job/TaskGraph/Loop execution model.</p>
 */
@Service
public class TurnRouter {

    private static final Pattern MIXED_INTENT_MARKER =
            Pattern.compile(".*(另外|然后|再帮我|顺便|对了|同时|还要|再写|再生成|再来).*");

    private final PendingInteractionRouter pendingInteractionRouter;
    private final IntentRecognitionService intentRecognitionService;

    /**
     * Creates a turn router.
     *
     * @param pendingInteractionRouter pending interaction recognizer
     * @param intentRecognitionService single intent recognizer
     */
    public TurnRouter(
            PendingInteractionRouter pendingInteractionRouter,
            IntentRecognitionService intentRecognitionService) {
        this.pendingInteractionRouter = pendingInteractionRouter;
        this.intentRecognitionService = intentRecognitionService;
    }

    /**
     * Routes one user message into executable Control actions.
     *
     * @param request routing request
     * @return ordered routing plan
     */
    public TurnRoutingPlan route(TurnRoutingRequest request) {
        PendingInteractionRoute pendingRoute = pendingInteractionRouter.route(
                new PendingInteractionRoutingRequest(
                        request.userMessage(),
                        request.promptView(),
                        request.pendingCandidates(),
                        request.modelRoutingAllowed()));
        if (pendingRoute.routeType()
                == PendingInteractionRouteType.EXPLAIN_PENDING_REQUIREMENTS
                && pendingRoute.targetId() != null) {
            return TurnRoutingPlan.single(new TurnAction(
                    TurnActionType.EXPLAIN_PENDING_REQUIREMENTS,
                    pendingRoute.targetId(),
                    "",
                    PendingInteractionFacts.empty(),
                    null,
                    pendingRoute.userFacingMessage(),
                    pendingRoute.auditSummary()));
        }
        if (pendingRoute.targetsWaitingInteraction()) {
            return pendingAnswerPlan(request, pendingRoute);
        }
        if (pendingRoute.routeType() == PendingInteractionRouteType.AMBIGUOUS) {
            return TurnRoutingPlan.single(new TurnAction(
                    TurnActionType.ASK_DISAMBIGUATION,
                    null,
                    "",
                    pendingRoute.facts(),
                    null,
                    pendingRoute.userFacingMessage().isBlank()
                            ? pendingRoute.auditSummary()
                            : pendingRoute.userFacingMessage(),
                    pendingRoute.auditSummary()));
        }
        IntentRecognition recognition = recognizeIntent(request);
        if (recognition.intentType() == IntentType.CLARIFICATION_ANSWER) {
            return clarificationAnswerPlan(request, recognition);
        }
        if (recognition.createsJob()) {
            return TurnRoutingPlan.single(TurnAction.createJob(recognition));
        }
        return TurnRoutingPlan.single(TurnAction.controlMessage(recognition));
    }

    /**
     * Builds a plan for a message that answers a pending interaction.
     */
    private TurnRoutingPlan pendingAnswerPlan(
            TurnRoutingRequest request,
            PendingInteractionRoute pendingRoute) {
        List<TurnAction> actions = new ArrayList<>();
        actions.add(TurnAction.answerPending(
                pendingRoute.targetId(),
                answerText(request.userMessage(), pendingRoute),
                pendingRoute.facts(),
                pendingRoute.auditSummary()));
        if (mayContainAdditionalIntent(request.userMessage())) {
            IntentRecognition recognition = recognizeIntent(request);
            // The extra CREATE_JOB action is conservative: a mixed marker must
            // be present, and the normal intent recognizer must still say the
            // same message creates a Job.
            if (recognition.createsJob()
                    && recognition.intentType() != IntentType.CLARIFICATION_ANSWER) {
                actions.add(TurnAction.createJob(recognition));
            }
        }
        return new TurnRoutingPlan(
                actions,
                actions.size() == 1
                        ? pendingRoute.auditSummary()
                        : "混合轮次：先回答等待澄清，再处理新增任务。");
    }

    /**
     * Handles a clarification answer recognized by the general intent pipeline.
     */
    private TurnRoutingPlan clarificationAnswerPlan(
            TurnRoutingRequest request,
            IntentRecognition recognition) {
        if (request.pendingCandidates().size() == 1) {
            UUID targetId = request.pendingCandidates().getFirst().id();
            return TurnRoutingPlan.single(TurnAction.answerPending(
                    targetId,
                    request.userMessage(),
                    PendingInteractionFacts.empty(),
                    "模型判定当前消息是唯一等待项的澄清回答。"));
        }
        if (!request.pendingCandidates().isEmpty()) {
            return TurnRoutingPlan.single(new TurnAction(
                    TurnActionType.ASK_DISAMBIGUATION,
                    null,
                    "",
                    PendingInteractionFacts.empty(),
                    recognition,
                    "模型判断当前消息像澄清回答，但存在多个等待项，需要先选择目标。",
                    "澄清回答存在多个候选等待项。"));
        }
        return TurnRoutingPlan.single(new TurnAction(
                TurnActionType.CLARIFICATION_NO_TARGET,
                null,
                "",
                PendingInteractionFacts.empty(),
                recognition,
                "",
                "澄清回答没有可恢复目标。"));
    }

    /**
     * Runs the existing intent recognizer.
     */
    private IntentRecognition recognizeIntent(TurnRoutingRequest request) {
        return intentRecognitionService.recognize(new IntentRecognitionRequest(
                request.userMessage(),
                request.promptView(),
                request.activeJobId(),
                request.modelRoutingAllowed()));
    }

    /**
     * Returns the answer part that should be bound to a pending interaction.
     */
    private String answerText(
            String currentContent,
            PendingInteractionRoute route) {
        if (!route.answerText().isBlank()) {
            return route.answerText();
        }
        return beforeMixedMarker(currentContent);
    }

    /**
     * Checks whether the message may contain a second user intent.
     */
    private boolean mayContainAdditionalIntent(String content) {
        return content != null
                && MIXED_INTENT_MARKER.matcher(content).matches();
    }

    /**
     * Keeps the pending-answer part before common mixed-intent markers.
     */
    private String beforeMixedMarker(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String[] markers = {"另外", "然后", "再帮我", "顺便", "对了", "同时", "还要"};
        int splitIndex = content.length();
        for (String marker : markers) {
            int index = content.indexOf(marker);
            if (index > 0 && index < splitIndex) {
                splitIndex = index;
            }
        }
        return content.substring(0, splitIndex).trim();
    }
}
