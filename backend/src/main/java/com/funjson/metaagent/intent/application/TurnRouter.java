package com.funjson.metaagent.intent.application;

import com.funjson.metaagent.intent.domain.TurnRoutingPlan;
import com.funjson.metaagent.intent.domain.TurnRoutingRequest;
import org.springframework.stereotype.Service;

/**
 * Facade that routes one user turn through formal turn understanding.
 *
 * <p>The previous keyword-based mixed-intent branch has been replaced by
 * {@link TurnUnderstandingService}. Pending interactions, mixed actions and
 * task-level intent rewrite are understood together before Control executes
 * the resulting {@link TurnRoutingPlan}.</p>
 */
@Service
public class TurnRouter {

    private final TurnUnderstandingService turnUnderstandingService;

    /**
     * Creates the router facade.
     *
     * @param turnUnderstandingService formal turn understanding service
     */
    public TurnRouter(TurnUnderstandingService turnUnderstandingService) {
        this.turnUnderstandingService = turnUnderstandingService;
    }

    /**
     * Routes one user message into executable Control actions.
     *
     * @param request routing request
     * @return ordered routing plan
     */
    public TurnRoutingPlan route(TurnRoutingRequest request) {
        return turnUnderstandingService.route(request);
    }
}
