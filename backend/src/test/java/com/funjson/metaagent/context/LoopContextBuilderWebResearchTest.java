package com.funjson.metaagent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.capability.domain.CapabilityPlanningContext;
import com.funjson.metaagent.context.application.ContextAssembler;
import com.funjson.metaagent.context.application.LoopContextBuilder;
import com.funjson.metaagent.context.application.TaskScopedContextProjector;
import com.funjson.metaagent.context.domain.ContextBlockType;
import com.funjson.metaagent.file.application.FileAttachmentService;
import com.funjson.metaagent.loop.domain.LoopRunParentType;
import com.funjson.metaagent.loop.domain.RunExecutionContext;
import com.funjson.metaagent.runtime.application.CurrentTimeContextProvider;
import com.funjson.metaagent.runtime.application.port.out.TaskIntentScopeStore;
import com.funjson.metaagent.tool.application.ToolCatalogService;
import com.funjson.metaagent.tool.application.port.out.ToolStore;
import com.funjson.metaagent.websearch.application.port.out.WebResearchStore;
import org.junit.jupiter.api.Test;

/**
 * Verifies that persisted web research artifacts are injected into Loop context.
 */
class LoopContextBuilderWebResearchTest {

    @Test
    void injectsWebResearchEvidencePoolAsObservation() {
        ToolCatalogService toolCatalogService = mock(ToolCatalogService.class);
        ToolStore toolStore = mock(ToolStore.class);
        WebResearchStore webResearchStore = mock(WebResearchStore.class);
        TaskIntentScopeStore taskIntentScopes = mock(TaskIntentScopeStore.class);
        UUID jobId = UUID.randomUUID();
        LoopContextBuilder builder = new LoopContextBuilder(
                toolCatalogService,
                toolStore,
                mock(ContextAssembler.class),
                mock(FileAttachmentService.class),
                webResearchStore,
                new CurrentTimeContextProvider(),
                new TaskScopedContextProjector(),
                taskIntentScopes);

        when(toolStore.findConversationIdByJobId(jobId))
                .thenReturn(Optional.empty());
        when(taskIntentScopes.findByJobId(jobId)).thenReturn(Optional.empty());
        when(toolCatalogService.promptSummary()).thenReturn("web.search");
        when(webResearchStore.summarizeForJob(jobId, 24))
                .thenReturn("- EVIDENCE · Official: useful fact · https://example.com");

        var snapshot = builder.build(
                context(jobId),
                CapabilityPlanningContext.empty());

        assertThat(snapshot.blocks())
                .anySatisfy(block -> {
                    assertThat(block.type()).isEqualTo(ContextBlockType.OBSERVATION);
                    assertThat(block.title()).isEqualTo(
                            "Web Research Evidence Pool");
                    assertThat(block.content()).contains(
                            "useful fact",
                            "搜索候选不等于已验证证据");
                });
    }

    @Test
    void injectsCurrentTimeAsConstraint() {
        ToolCatalogService toolCatalogService = mock(ToolCatalogService.class);
        ToolStore toolStore = mock(ToolStore.class);
        WebResearchStore webResearchStore = mock(WebResearchStore.class);
        TaskIntentScopeStore taskIntentScopes = mock(TaskIntentScopeStore.class);
        UUID jobId = UUID.randomUUID();
        LoopContextBuilder builder = new LoopContextBuilder(
                toolCatalogService,
                toolStore,
                mock(ContextAssembler.class),
                mock(FileAttachmentService.class),
                webResearchStore,
                new CurrentTimeContextProvider(),
                new TaskScopedContextProjector(),
                taskIntentScopes);

        when(toolStore.findConversationIdByJobId(jobId))
                .thenReturn(Optional.empty());
        when(taskIntentScopes.findByJobId(jobId)).thenReturn(Optional.empty());
        when(toolCatalogService.promptSummary()).thenReturn("weather.current");
        when(webResearchStore.summarizeForJob(jobId, 24)).thenReturn("");

        var snapshot = builder.build(
                context(jobId),
                CapabilityPlanningContext.empty());

        assertThat(snapshot.blocks())
                .anySatisfy(block -> {
                    assertThat(block.type()).isEqualTo(ContextBlockType.CONSTRAINT);
                    assertThat(block.title()).isEqualTo("Current Time");
                    assertThat(block.content()).contains(
                            "today:",
                            "currentYear:",
                            "用户明确给出绝对日期",
                            "不要凭空给查询或结论添加其他年份");
                });
    }

    /**
     * Creates a minimal execution context for context assembly tests.
     */
    private RunExecutionContext context(UUID jobId) {
        return new RunExecutionContext(
                jobId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                0,
                1,
                LoopRunParentType.TASK_RUN,
                UUID.randomUUID(),
                0,
                "fake-chat",
                "研究目标",
                "",
                null);
    }
}
