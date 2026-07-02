package com.funjson.metaagent.intent.application;

import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.intent.domain.IntentRecognition;
import com.funjson.metaagent.intent.domain.IntentRecognitionRequest;
import com.funjson.metaagent.intent.domain.IntentRiskLevel;
import com.funjson.metaagent.intent.domain.IntentType;
import com.funjson.metaagent.intent.domain.PendingInteractionFacts;
import com.funjson.metaagent.intent.domain.PendingInteractionRoute;
import com.funjson.metaagent.intent.domain.PendingInteractionRouteType;
import com.funjson.metaagent.intent.domain.PendingInteractionRoutingRequest;
import com.funjson.metaagent.intent.domain.TurnAction;
import com.funjson.metaagent.intent.domain.TurnActionType;
import com.funjson.metaagent.intent.domain.TurnRoutingRequest;
import com.funjson.metaagent.intent.domain.TurnUnderstanding;
import org.springframework.stereotype.Service;

/**
 * Deterministic fallback router for single-action turns.
 *
 * <p>This is not the main mixed-intent engine. It only preserves safe behavior
 * when the model turn understanding adapter is unavailable or returns an
 * invalid contract.</p>
 */
@Service
public class RuleTurnFallbackRouter {

    private final PendingInteractionRouter pendingInteractionRouter;
    private final IntentRecognitionService intentRecognitionService;

    /**
     * Creates a fallback router.
     *
     * @param pendingInteractionRouter pending interaction router
     * @param intentRecognitionService single-intent recognizer
     */
    public RuleTurnFallbackRouter(
            PendingInteractionRouter pendingInteractionRouter,
            IntentRecognitionService intentRecognitionService) {
        this.pendingInteractionRouter = pendingInteractionRouter;
        this.intentRecognitionService = intentRecognitionService;
    }

    /**
     * Builds a conservative single-action understanding.
     *
     * @param request turn request
     * @return fallback understanding
     */
    public TurnUnderstanding route(TurnRoutingRequest request) {
        PendingInteractionRoute pendingRoute = pendingInteractionRouter.route(
                new PendingInteractionRoutingRequest(
                        request.userMessage(),
                        request.promptView(),
                        request.pendingCandidates(),
                        request.modelRoutingAllowed()));
        if (pendingRoute.routeType()
                == PendingInteractionRouteType.EXPLAIN_PENDING_REQUIREMENTS
                && pendingRoute.targetId() != null) {
            return single(new TurnAction(
                    TurnActionType.EXPLAIN_PENDING_REQUIREMENTS,
                    pendingRoute.targetId(),
                    "",
                    PendingInteractionFacts.empty(),
                    null,
                    pendingRoute.userFacingMessage(),
                    pendingRoute.auditSummary()));
        }
        if (pendingRoute.targetsWaitingInteraction()) {
            return single(TurnAction.answerPending(
                    pendingRoute.targetId(),
                    answerText(request.userMessage(), pendingRoute),
                    pendingRoute.facts(),
                    pendingRoute.auditSummary()));
        }
        if (pendingRoute.routeType() == PendingInteractionRouteType.AMBIGUOUS) {
            return single(new TurnAction(
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
            return clarificationAnswerUnderstanding(
                    request,
                    recognition);
        }
        if (recognition.createsJob()) {
            return single(TurnAction.createJob(
                    recognition,
                    request.userMessage(),
                    request.userMessage(),
                    recognition.goalSummary(),
                    com.funjson.metaagent.intent.domain.IntentRewrite.none()));
        }
        return single(TurnAction.controlMessage(recognition));
    }

    /**
     * Handles a clarification answer recognized by the general intent pipeline.
     */
    private TurnUnderstanding clarificationAnswerUnderstanding(
            TurnRoutingRequest request,
            IntentRecognition recognition) {
        if (request.pendingCandidates().size() == 1) {
            UUID targetId = request.pendingCandidates().getFirst().id();
            return single(TurnAction.answerPending(
                    targetId,
                    request.userMessage(),
                    PendingInteractionFacts.empty(),
                    "模型判定当前消息是唯一等待项的澄清回答。"));
        }
        if (!request.pendingCandidates().isEmpty()) {
            return single(new TurnAction(
                    TurnActionType.ASK_DISAMBIGUATION,
                    null,
                    "",
                    PendingInteractionFacts.empty(),
                    recognition,
                    "模型判断当前消息像澄清回答，但存在多个等待项，需要先选择目标。",
                    "澄清回答存在多个候选等待项。"));
        }
        return single(new TurnAction(
                TurnActionType.CLARIFICATION_NO_TARGET,
                null,
                "",
                PendingInteractionFacts.empty(),
                recognition,
                "",
                "澄清回答没有可恢复目标。"));
    }

    /**
     * Runs the existing single-intent recognizer.
     */
    private IntentRecognition recognizeIntent(TurnRoutingRequest request) {
        return intentRecognitionService.recognize(new IntentRecognitionRequest(
                request.userMessage(),
                request.promptView(),
                request.activeJobId(),
                request.modelRoutingAllowed()));
    }

    /**
     * Returns the pending answer text selected by the router.
     */
    private String answerText(
            String currentContent,
            PendingInteractionRoute route) {
        if (!route.answerText().isBlank()) {
            return route.answerText();
        }
        return currentContent;
    }

    /**
     * Wraps one action into a turn understanding.
     */
    private TurnUnderstanding single(TurnAction action) {
        return new TurnUnderstanding(
                List.of(action),
                action.auditSummary());
    }
}
