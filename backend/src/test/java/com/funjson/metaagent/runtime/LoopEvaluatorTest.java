package com.funjson.metaagent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.funjson.metaagent.loop.domain.LoopActionResult;
import com.funjson.metaagent.loop.domain.LoopActionType;
import com.funjson.metaagent.loop.domain.ClarificationNeedDetector;
import com.funjson.metaagent.loop.domain.LoopEvaluationDecision;
import com.funjson.metaagent.loop.domain.LoopEvaluator;
import com.funjson.metaagent.loop.domain.LoopExecutionPolicy;
import com.funjson.metaagent.loop.domain.LoopRunParentType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import org.junit.jupiter.api.Test;

/**
 * 验证 Loop Evaluation 的完成、调整和预算失败决策。
 */
class LoopEvaluatorTest {

    private final LoopEvaluator evaluator =
            new LoopEvaluator(new ClarificationNeedDetector());

    @Test
    void completesWhenProviderReturnsUserFacingContent() {
        var evaluation = evaluator.evaluate(
                context(0, 1),
                actionResult("done"),
                LoopExecutionPolicy.baseline(),
                1);

        assertThat(evaluation.decision())
                .isEqualTo(LoopEvaluationDecision.COMPLETE);
    }

    @Test
    void clarificationAnswerAdjustsInsteadOfCompleting() {
        var evaluation = evaluator.evaluate(
                context(0, 1),
                clarificationResult("求职面试，自由职业者，做 Java，轻松一些，100 字左右。"),
                LoopExecutionPolicy.baseline(),
                1);

        assertThat(evaluation.decision())
                .isEqualTo(LoopEvaluationDecision.ADJUST);
        assertThat(evaluation.summary()).contains("重新执行模型动作");
        assertThat(evaluation.feedback()).contains("最终结果");
    }

    @Test
    void doesNotCompleteWhenModelAsksForMissingInput() {
        var evaluation = evaluator.evaluate(
                context(0, 1),
                actionResult("为了生成准确结果，请补充用途和风格。"),
                LoopExecutionPolicy.baseline(),
                1);

        assertThat(evaluation.decision())
                .isEqualTo(LoopEvaluationDecision.ADJUST);
        assertThat(evaluation.feedback()).contains("clarification.request");
    }

    @Test
    void doesNotCompleteWhenModelNaturallyAsksForMoreDetails() {
        var evaluation = evaluator.evaluate(
                context(0, 1),
                actionResult("光有名字还不够，我还需要了解用途、背景、风格和长度。请你补充一下。"),
                LoopExecutionPolicy.baseline(),
                1);

        assertThat(evaluation.decision())
                .isEqualTo(LoopEvaluationDecision.ADJUST);
        assertThat(evaluation.feedback()).contains("clarification.request");
    }

    @Test
    void doesNotCompleteWhenResultLeaksInternalRuntimeTerms() {
        var evaluation = evaluator.evaluate(
                context(0, 1),
                actionResult("当前 LoopNode 已完成，Observation 已记录。"),
                LoopExecutionPolicy.baseline(),
                1);

        assertThat(evaluation.decision())
                .isEqualTo(LoopEvaluationDecision.ADJUST);
        assertThat(evaluation.feedback()).contains("内部执行术语");
    }

    @Test
    void adjustsWhenEmptyResultStillHasBudget() {
        var evaluation = evaluator.evaluate(
                context(0, 1),
                actionResult(""),
                LoopExecutionPolicy.baseline(),
                1);

        assertThat(evaluation.decision())
                .isEqualTo(LoopEvaluationDecision.ADJUST);
        assertThat(evaluation.feedback()).isNotBlank();
    }

    @Test
    void failsWhenEmptyResultExhaustsBudget() {
        var evaluation = evaluator.evaluate(
                context(2, 3),
                actionResult(""),
                LoopExecutionPolicy.baseline(),
                3);

        assertThat(evaluation.decision())
                .isEqualTo(LoopEvaluationDecision.FAIL);
    }

    /**
     * 创建测试节点上下文。
     *
     * @param depth 深度
     * @param iterationNo 迭代号
     * @return 上下文
     */
    private RunExecutionContext context(
            int depth,
            int iterationNo) {
        return new RunExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                depth,
                iterationNo,
                LoopRunParentType.TASK_RUN,
                UUID.randomUUID(),
                0,
                "fake",
                "goal",
                "",
                null);
    }

    /**
     * 创建测试动作结果。
     *
     * @param content 内容
     * @return 动作结果
     */
    private LoopActionResult actionResult(String content) {
        return new LoopActionResult(
                LoopActionType.MODEL_CALL,
                "fake/fake-deterministic-v1",
                content,
                java.util.Map.of());
    }

    /**
     * 创建澄清回答动作结果。
     *
     * @param content 用户澄清回答
     * @return 动作结果
     */
    private LoopActionResult clarificationResult(String content) {
        return new LoopActionResult(
                LoopActionType.CLARIFICATION_REQUEST,
                "clarification:test",
                content,
                java.util.Map.of("clarificationRequestId", UUID.randomUUID()));
    }
}
