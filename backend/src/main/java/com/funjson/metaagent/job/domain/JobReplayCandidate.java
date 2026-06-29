package com.funjson.metaagent.job.domain;

import java.util.UUID;

/**
 * A Job that was created but not yet materialized into a TaskRun.
 *
 * @param jobId Job ID
 * @param conversationId Conversation ID
 * @param sourceMessageId source user message ID
 * @param version current Job version
 */
public record JobReplayCandidate(
        UUID jobId,
        UUID conversationId,
        UUID sourceMessageId,
        long version) {
}
