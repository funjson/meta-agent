package com.funjson.metaagent.websearch.domain;

import java.util.UUID;

/**
 * Runtime identity that connects web research artifacts back to the executing
 * LoopNode and ToolInvocation.
 *
 * @param toolInvocationId ToolInvocation that produced the artifact
 * @param jobId owning Job, optional for direct tool tests
 * @param taskId owning Task, optional for direct tool tests
 * @param taskRunId owning TaskRun, optional for direct tool tests
 * @param loopRunId owning LoopRun, optional for direct tool tests
 * @param loopNodeId owning LoopNode, optional for direct tool tests
 */
public record WebResearchContext(
        UUID toolInvocationId,
        UUID jobId,
        UUID taskId,
        UUID taskRunId,
        UUID loopRunId,
        UUID loopNodeId) {

    /**
     * Ensures every persisted artifact can be traced to a tool call.
     */
    public WebResearchContext {
        if (toolInvocationId == null) {
            throw new IllegalArgumentException(
                    "ToolInvocation ID is required for web evidence");
        }
    }
}
