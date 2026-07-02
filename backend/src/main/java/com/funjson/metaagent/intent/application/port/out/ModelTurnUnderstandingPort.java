package com.funjson.metaagent.intent.application.port.out;

import java.util.Optional;

import com.funjson.metaagent.intent.domain.TurnRoutingRequest;
import com.funjson.metaagent.intent.domain.TurnUnderstanding;

/**
 * Port for model-backed full turn understanding.
 */
public interface ModelTurnUnderstandingPort {

    /**
     * Understands one complete user turn, including mixed intents and rewrites.
     *
     * @param request turn routing request
     * @return model-generated understanding when available and parseable
     */
    Optional<TurnUnderstanding> understand(TurnRoutingRequest request);
}
