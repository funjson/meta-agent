package com.funjson.metaagent.clarification.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.funjson.metaagent.clarification.domain.ClarificationRequestDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 澄清请求 MyBatis Mapper。
 */
@Mapper
public interface ClarificationPersistenceMapper {

    /** @return Job 下打开的澄清请求行 */
    Map<String, Object> findOpenByJobId(@Param("jobId") UUID jobId);

    /** @return Conversation 下打开的澄清请求行 */
    List<Map<String, Object>> findOpenByConversationId(
            @Param("conversationId") UUID conversationId);

    /** @return Conversation 下最近已解决的澄清请求行 */
    List<Map<String, Object>> findRecentResolvedByConversationId(
            @Param("conversationId") UUID conversationId,
            @Param("limit") int limit);

    /** @return 指定来源下打开的澄清请求行 */
    List<Map<String, Object>> findOpenBySource(
            @Param("jobId") UUID jobId,
            @Param("taskId") UUID taskId,
            @Param("taskRunId") UUID taskRunId,
            @Param("loopNodeId") UUID loopNodeId);

    /** 插入澄清请求。 */
    int insert(
            @Param("id") UUID id,
            @Param("conversationId") UUID conversationId,
            @Param("jobId") UUID jobId,
            @Param("taskId") UUID taskId,
            @Param("taskRunId") UUID taskRunId,
            @Param("loopNodeId") UUID loopNodeId,
            @Param("draft") ClarificationRequestDraft draft);

    /** @return 更新行数 */
    int answer(
            @Param("clarificationRequestId") UUID clarificationRequestId,
            @Param("answerMessageId") UUID answerMessageId,
            @Param("answer") String answer);

    /** @return 更新行数 */
    int recordPartialAnswer(
            @Param("clarificationRequestId") UUID clarificationRequestId,
            @Param("answerMessageId") UUID answerMessageId,
            @Param("answer") String answer,
            @Param("partialResolutionJson") String partialResolutionJson);

    /** @return 更新行数 */
    int resolve(
            @Param("clarificationRequestId") UUID clarificationRequestId,
            @Param("resolutionJson") String resolutionJson);
}
