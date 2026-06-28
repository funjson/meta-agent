package com.funjson.metaagent.control.application.port.out;

import com.funjson.metaagent.control.api.ControlDecisionView;
import com.funjson.metaagent.control.domain.ControlTurn;
import com.funjson.metaagent.intent.domain.IntentRecognition;

import java.util.Optional;
import java.util.UUID;

/**
 * ControlTurn 与 ControlDecision 的持久化端口。
 */
public interface ControlTurnStore {

    /** @return 幂等键关联的 ControlTurn */
    Optional<ControlTurn> findByIdempotencyKey(String idempotencyKey);

    /** @return ControlTurn 的结构化决策 */
    Optional<ControlDecisionView> findDecision(UUID controlTurnId);

    /** 插入初始化中的 ControlTurn。 */
    void insertTurn(
            UUID controlTurnId,
            UUID conversationId,
            UUID sourceMessageId,
            String idempotencyKey);

    /** 关联本轮创建的 Job。 */
    void attachJob(UUID controlTurnId, UUID jobId);

    /** 插入 ControlDecision。 */
    void insertDecision(
            UUID decisionId,
            UUID controlTurnId,
            UUID conversationId,
            UUID sourceMessageId,
            UUID jobId,
            IntentRecognition recognition,
            String constraintsJson,
            String taskGraphJson);

    /** 将 ControlTurn 标记为完成。 */
    void completeTurn(UUID controlTurnId);
}
