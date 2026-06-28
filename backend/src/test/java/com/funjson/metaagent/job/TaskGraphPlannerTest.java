package com.funjson.metaagent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.funjson.metaagent.job.application.TaskGraphPlanner;
import com.funjson.metaagent.job.application.port.out.TaskGraphPlanningPort;
import com.funjson.metaagent.job.domain.TaskGraphNodePlan;
import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.job.domain.TaskGraphPlanningRequest;
import com.funjson.metaagent.job.domain.TaskGraphValidator;
import com.funjson.metaagent.task.domain.TaskStatus;
import org.junit.jupiter.api.Test;

/**
 * 验证 Control 根据意图属性选择 Task Graph 规划策略。
 */
class TaskGraphPlannerTest {

    @Test
    void createsSingleReadyTaskWithoutCallingModel() {
        TaskGraphPlanningPort port =
                mock(TaskGraphPlanningPort.class);
        TaskGraphPlanner planner = new TaskGraphPlanner(
                port,
                new TaskGraphValidator());
        TaskGraphPlanningRequest request =
                request(false, false, false);

        TaskGraphPlan result = planner.plan(request);

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.nodes().getFirst().initialStatus())
                .isEqualTo(TaskStatus.READY);
        verify(port, never()).plan(request);
    }

    @Test
    void acceptsValidatedModelGraphForCompoundTask() {
        TaskGraphPlanningPort port =
                mock(TaskGraphPlanningPort.class);
        TaskGraphPlanner planner = new TaskGraphPlanner(
                port,
                new TaskGraphValidator());
        TaskGraphPlanningRequest request =
                request(false, true, true);
        TaskGraphPlan candidate = new TaskGraphPlan(
                "MODEL_DECOMPOSITION",
                "two tasks",
                List.of(
                        node("research", List.of()),
                        node("implement", List.of("research"))),
                null);
        when(port.plan(request)).thenReturn(Optional.of(candidate));

        TaskGraphPlan result = planner.plan(request);

        assertThat(result.source())
                .isEqualTo("MODEL_DECOMPOSITION");
        assertThat(result.nodes().get(1).initialStatus())
                .isEqualTo(TaskStatus.BLOCKED);
        assertThat(result.nodes().get(1).goal())
                .contains("build the platform")
                .contains("java");
    }

    @Test
    void fallsBackToWaitingWhenModelGraphIsInvalid() {
        TaskGraphPlanningPort port =
                mock(TaskGraphPlanningPort.class);
        TaskGraphPlanner planner = new TaskGraphPlanner(
                port,
                new TaskGraphValidator());
        TaskGraphPlanningRequest request =
                request(false, true, true);
        TaskGraphPlan invalid = new TaskGraphPlan(
                "MODEL_DECOMPOSITION",
                "cycle",
                List.of(
                        node("a", List.of("b")),
                        node("b", List.of("a"))),
                null);
        when(port.plan(request)).thenReturn(Optional.of(invalid));

        TaskGraphPlan result = planner.plan(request);

        assertThat(result.source()).isEqualTo("DECOMPOSITION_FAILED");
        assertThat(result.nodes().getFirst().initialStatus())
                .isEqualTo(TaskStatus.WAITING_HUMAN);
        assertThat(result.clarification()).isPresent();
    }

    @Test
    void usesConcreteClarificationQuestionFromIntent() {
        TaskGraphPlanningPort port =
                mock(TaskGraphPlanningPort.class);
        TaskGraphPlanner planner = new TaskGraphPlanner(
                port,
                new TaskGraphValidator());
        TaskGraphPlanningRequest request = new TaskGraphPlanningRequest(
                "write an introduction",
                "write introduction",
                List.of(),
                "请补充使用场景、个人背景和期望风格。",
                true,
                false,
                false);

        TaskGraphPlan result = planner.plan(request);

        assertThat(result.clarification()
                        .map(draft -> draft.question()))
                .contains("请补充使用场景、个人背景和期望风格。");
    }

    /** 创建测试规划请求。 */
    private TaskGraphPlanningRequest request(
            boolean clarification,
            boolean compound,
            boolean modelAllowed) {
        return new TaskGraphPlanningRequest(
                "build the platform",
                "build platform",
                List.of("java"),
                "",
                clarification,
                compound,
                modelAllowed);
    }

    /** 创建初始状态与依赖一致的测试节点。 */
    private TaskGraphNodePlan node(
            String key,
            List<String> dependencies) {
        return new TaskGraphNodePlan(
                key,
                key,
                "goal-" + key,
                dependencies.isEmpty()
                        ? TaskStatus.READY
                        : TaskStatus.BLOCKED,
                "LOOP",
                dependencies);
    }
}
