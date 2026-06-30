package com.funjson.metaagent.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.funjson.metaagent.job.application.DefaultResearchTaskGraphFactory;
import com.funjson.metaagent.job.domain.TaskGraphValidator;
import com.funjson.metaagent.task.domain.TaskStatus;
import org.junit.jupiter.api.Test;

/**
 * Verifies the framework default Deep Research TaskGraph.
 */
class DefaultResearchTaskGraphFactoryTest {

    private final DefaultResearchTaskGraphFactory factory =
            new DefaultResearchTaskGraphFactory(new TaskGraphValidator());

    @Test
    void supportsOnlyExplicitDeepResearchLabel() {
        assertThat(factory.supports(List.of(
                "needs-web",
                "research-depth:deep-research"))).isTrue();
        assertThat(factory.supports(List.of(
                "needs-web",
                "research-depth:search-qa"))).isFalse();
    }

    @Test
    void createsValidatedLinearDeepResearchGraph() {
        var graph = factory.create(
                "请深度研究生产级 web search 怎么做",
                "研究生产级 Web Search",
                List.of("需要引用来源"));

        assertThat(graph.source())
                .isEqualTo(DefaultResearchTaskGraphFactory.SOURCE);
        assertThat(graph.nodes())
                .extracting(node -> node.key())
                .containsExactly(
                        "research-plan",
                        "source-discovery",
                        "source-reading",
                        "evidence-matrix",
                        "report-synthesis",
                        "quality-review");
        assertThat(graph.nodes().getFirst().initialStatus())
                .isEqualTo(TaskStatus.READY);
        assertThat(graph.nodes().get(1).initialStatus())
                .isEqualTo(TaskStatus.BLOCKED);
        assertThat(graph.nodes().get(5).dependsOnKeys())
                .containsExactly("report-synthesis");
        assertThat(graph.nodes().get(2).goal())
                .contains("web.fetch")
                .contains("web.extract")
                .contains("搜索摘要不能当成最终证据");
    }
}
