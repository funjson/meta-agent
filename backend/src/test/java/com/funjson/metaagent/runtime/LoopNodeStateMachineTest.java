package com.funjson.metaagent.runtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funjson.metaagent.loop.domain.LoopNodeStateMachine;
import com.funjson.metaagent.loop.domain.LoopNodeStatus;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.junit.jupiter.api.Test;

/**
 * 验证 LoopNode 状态所有权与合法迁移。
 */
class LoopNodeStateMachineTest {

    private final LoopNodeStateMachine stateMachine =
            new LoopNodeStateMachine();

    @Test
    void permitsChildWaitAndCompletionTransitions() {
        assertThatCode(() -> {
            stateMachine.requireTransition(
                    LoopNodeStatus.RUNNING,
                    LoopNodeStatus.WAITING_CHILDREN);
            stateMachine.requireTransition(
                    LoopNodeStatus.WAITING_CHILDREN,
                    LoopNodeStatus.RUNNING);
            stateMachine.requireTransition(
                    LoopNodeStatus.RUNNING,
                    LoopNodeStatus.WAITING_CHILDREN);
            stateMachine.requireTransition(
                    LoopNodeStatus.WAITING_CHILDREN,
                    LoopNodeStatus.COMPLETED);
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsCompletedNodeRestart() {
        assertThatThrownBy(() -> stateMachine.requireTransition(
                LoopNodeStatus.COMPLETED,
                LoopNodeStatus.RUNNING))
                .isInstanceOf(RuntimeStateException.class)
                .extracting(exception ->
                        ((RuntimeStateException) exception).code())
                .isEqualTo("INVALID_STATE_TRANSITION");
    }
}
