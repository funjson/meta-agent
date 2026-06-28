package com.funjson.metaagent.control.infrastructure.persistence.mybatis;

import com.funjson.metaagent.intent.domain.IntentRecognition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;
import java.util.UUID;

/**
 * ControlTurn 与 ControlDecision 的 MyBatis Mapper。
 */
@Mapper
public interface ControlTurnPersistenceMapper {

    /** @return 幂等键对应的 ControlTurn 行 */
    Map<String, Object> findTurnByIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey);

    /** @return ControlTurn 对应的决策行 */
    Map<String, Object> findDecision(
            @Param("controlTurnId") UUID controlTurnId);

    /** 插入 ControlTurn。 */
    int insertTurn(
            @Param("controlTurnId") UUID controlTurnId,
            @Param("conversationId") UUID conversationId,
            @Param("sourceMessageId") UUID sourceMessageId,
            @Param("idempotencyKey") String idempotencyKey);

    /** 关联 Job。 */
    int attachJob(
            @Param("controlTurnId") UUID controlTurnId,
            @Param("jobId") UUID jobId);

    /** 插入 ControlDecision。 */
    int insertDecision(
            @Param("decisionId") UUID decisionId,
            @Param("controlTurnId") UUID controlTurnId,
            @Param("conversationId") UUID conversationId,
            @Param("sourceMessageId") UUID sourceMessageId,
            @Param("jobId") UUID jobId,
            @Param("recognition") IntentRecognition recognition,
            @Param("constraintsJson") String constraintsJson,
            @Param("taskGraphJson") String taskGraphJson);

    /** 完成 ControlTurn。 */
    int completeTurn(@Param("controlTurnId") UUID controlTurnId);
}
