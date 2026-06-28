package com.funjson.metaagent.clarification.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.clarification.domain.ClarificationRequest;
import com.funjson.metaagent.clarification.domain.ClarificationRequestDraft;

/**
 * 澄清请求持久化端口。
 */
public interface ClarificationStore {

    /** @return 指定 Job 下未完成的澄清请求 */
    Optional<ClarificationRequest> findOpenByJobId(UUID jobId);

    /** @return Conversation 下全部未完成的澄清请求 */
    List<ClarificationRequest> findOpenByConversationId(
            UUID conversationId);

    /** @return Conversation 下最近已解决的澄清请求 */
    List<ClarificationRequest> findRecentResolvedByConversationId(
            UUID conversationId,
            int limit);

    /** @return 指定来源下未完成澄清请求 */
    List<ClarificationRequest> findOpenBySource(
            UUID jobId,
            UUID taskId,
            UUID taskRunId,
            UUID loopNodeId);

    /** 插入新的澄清请求。 */
    void insert(
            UUID id,
            UUID conversationId,
            UUID jobId,
            UUID taskId,
            UUID taskRunId,
            UUID loopNodeId,
            ClarificationRequestDraft draft);

    /** 记录用户回答。 */
    void answer(
            UUID clarificationRequestId,
            UUID answerMessageId,
            String answer);

    /** 记录部分回答，但保持澄清请求继续打开。 */
    void recordPartialAnswer(
            UUID clarificationRequestId,
            UUID answerMessageId,
            String answer,
            String partialResolutionJson);

    /** 标记澄清已经恢复到原执行点。 */
    void resolve(
            UUID clarificationRequestId,
            String resolutionJson);
}
