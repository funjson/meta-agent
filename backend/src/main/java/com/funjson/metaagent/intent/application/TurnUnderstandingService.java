package com.funjson.metaagent.intent.application;

import java.util.Optional;

import com.funjson.metaagent.intent.application.port.out.ModelTurnUnderstandingPort;
import com.funjson.metaagent.intent.domain.TurnRoutingPlan;
import com.funjson.metaagent.intent.domain.TurnRoutingRequest;
import com.funjson.metaagent.intent.domain.TurnUnderstanding;
import org.springframework.stereotype.Service;

/**
 * Formal entry for complete user-turn understanding.
 *
 * <p>The service treats pending interactions, mixed intents and task-level
 * intent rewrite as one planning problem. It falls back to deterministic
 * single-action routing only when model understanding is unavailable or invalid.</p>
 */
@Service
public class TurnUnderstandingService {

    private final ModelTurnUnderstandingPort modelTurnUnderstanding;
    private final RuleTurnFallbackRouter fallbackRouter;
    private final TurnPlanValidator validator;

    /**
     * Creates the service.
     *
     * @param modelTurnUnderstanding model-backed understanding port
     * @param fallbackRouter deterministic fallback router
     * @param validator turn plan validator
     */
    public TurnUnderstandingService(
            ModelTurnUnderstandingPort modelTurnUnderstanding,
            RuleTurnFallbackRouter fallbackRouter,
            TurnPlanValidator validator) {
        this.modelTurnUnderstanding = modelTurnUnderstanding;
        this.fallbackRouter = fallbackRouter;
        this.validator = validator;
    }

    /**
     * Understands one user turn and returns an executable routing plan.
     *
     * @param request routing request
     * @return validated Control routing plan
     */
    public TurnRoutingPlan route(TurnRoutingRequest request) {
        Optional<TurnUnderstanding> modelPlan =
                modelTurnUnderstanding.understand(request)
                        .flatMap(plan -> validateModelPlan(
                                request,
                                plan));
        return modelPlan
                .orElseGet(() -> validator.requireValid(
                        request,
                        fallbackRouter.route(request)))
                .toRoutingPlan();
    }

    /**
     * Validates a model plan without allowing validation failures to block
     * deterministic fallback.
     */
    private Optional<TurnUnderstanding> validateModelPlan(
            TurnRoutingRequest request,
            TurnUnderstanding plan) {
        try {
            return Optional.of(validator.requireValid(request, plan));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
