package com.funjson.metaagent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.funjson.metaagent.job.domain.TaskGraphNodePlan;
import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.job.domain.TaskGraphValidator;
import com.funjson.metaagent.task.domain.TaskStatus;
import org.junit.jupiter.api.Test;

/**
 * 验证 Task Graph 的依赖引用、初始状态和 DAG 不变量。
 */
class TaskGraphValidatorTest {

    private final TaskGraphValidator validator =
            new TaskGraphValidator();

    @Test
    void acceptsAcyclicGraphWithDependencyAlignedStatuses() {
        TaskGraphPlan plan = graph(
                node("research", List.of(), TaskStatus.READY),
                node(
                        "implement",
                        List.of("research"),
                        TaskStatus.BLOCKED));

        assertThat(validator.validate(plan)).isSameAs(plan);
    }

    @Test
    void rejectsCycles() {
        TaskGraphPlan plan = graph(
                node(
                        "research",
                        List.of("implement"),
                        TaskStatus.BLOCKED),
                node(
                        "implement",
                        List.of("research"),
                        TaskStatus.BLOCKED));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void rejectsReadyTaskWithIncompleteDependencies() {
        TaskGraphPlan plan = graph(
                node("research", List.of(), TaskStatus.READY),
                node(
                        "implement",
                        List.of("research"),
                        TaskStatus.READY));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initial status");
    }

    /** 创建测试 Task Graph。 */
    private TaskGraphPlan graph(TaskGraphNodePlan... nodes) {
        return new TaskGraphPlan(
                "TEST",
                "test graph",
                List.of(nodes),
                null);
    }

    /** 创建测试节点。 */
    private TaskGraphNodePlan node(
            String key,
            List<String> dependencies,
            TaskStatus status) {
        return new TaskGraphNodePlan(
                key,
                key,
                "goal-" + key,
                status,
                "LOOP",
                dependencies);
    }
}
