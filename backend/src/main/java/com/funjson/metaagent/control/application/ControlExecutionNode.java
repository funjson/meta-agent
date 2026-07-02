package com.funjson.metaagent.control.application;

import com.funjson.metaagent.intent.domain.TurnAction;
import com.funjson.metaagent.intent.domain.TurnIntentNodeKind;
import com.funjson.metaagent.intent.domain.TurnTaskType;

/**
 * Executable Control node compiled from a turn-intent graph node.
 *
 * @param nodeId stable node ID inside the current turn
 * @param nodeKind semantic node kind
 * @param taskType stable task category
 * @param action executable action
 */
public record ControlExecutionNode(
        String nodeId,
        TurnIntentNodeKind nodeKind,
        TurnTaskType taskType,
        TurnAction action) {

    /**
     * Normalizes node fields.
     */
    public ControlExecutionNode {
        nodeId = nodeId == null ? "" : nodeId.trim();
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException(
                    "ControlExecutionNode requires a nodeId");
        }
        if (action == null) {
            throw new IllegalArgumentException(
                    "ControlExecutionNode requires an action");
        }
    }
}
