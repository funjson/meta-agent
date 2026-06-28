package com.funjson.metaagent.loop.domain;

import java.util.Map;
import java.util.Set;

import com.funjson.metaagent.runtime.domain.RuntimeStateException;


/**
 * 集中定义 C3 LoopNode 的合法状态迁移。
 */
public class LoopNodeStateMachine {

    private static final Map<LoopNodeStatus, Set<LoopNodeStatus>> TRANSITIONS =
            Map.of(
                    LoopNodeStatus.RUNNING,
                    Set.of(
                            LoopNodeStatus.WAITING_CHILDREN,
                            LoopNodeStatus.WAITING_CHILD_JOB,
                            LoopNodeStatus.WAITING_HUMAN,
                            LoopNodeStatus.COMPLETED,
                            LoopNodeStatus.FAILED),
                    LoopNodeStatus.WAITING_CHILDREN,
                    Set.of(
                            LoopNodeStatus.RUNNING,
                            LoopNodeStatus.COMPLETED,
                            LoopNodeStatus.FAILED),
                    LoopNodeStatus.WAITING_CHILD_JOB,
                    Set.of(
                            LoopNodeStatus.RUNNING,
                            LoopNodeStatus.FAILED,
                            LoopNodeStatus.CANCELLED),
                    LoopNodeStatus.WAITING_HUMAN,
                    Set.of(
                            LoopNodeStatus.RUNNING,
                            LoopNodeStatus.FAILED,
                            LoopNodeStatus.CANCELLED));

    /**
     * 校验状态迁移是否合法。
     *
     * @param from 来源状态
     * @param to 目标状态
     */
    public void requireTransition(
            LoopNodeStatus from,
            LoopNodeStatus to) {
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new RuntimeStateException(
                    "INVALID_STATE_TRANSITION",
                    "LoopNode cannot transition from %s to %s"
                            .formatted(from, to));
        }
    }
}
