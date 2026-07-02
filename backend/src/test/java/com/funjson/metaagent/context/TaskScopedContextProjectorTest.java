package com.funjson.metaagent.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.context.application.TaskScopedContextProjector;
import com.funjson.metaagent.context.domain.ContextBlock;
import com.funjson.metaagent.context.domain.ContextConversation;
import com.funjson.metaagent.context.domain.ContextEnvelope;
import com.funjson.metaagent.context.domain.ContextFact;
import com.funjson.metaagent.context.domain.ContextMessage;
import com.funjson.metaagent.runtime.domain.TaskIntentScope;
import org.junit.jupiter.api.Test;

/**
 * Verifies that mixed-intent sibling jobs receive only their own structured
 * context while still sharing stable user identity facts.
 */
class TaskScopedContextProjectorTest {

    private final TaskScopedContextProjector projector =
            new TaskScopedContextProjector();

    @Test
    void profileTaskReceivesNameAndOwnFactsButNotWeatherFacts() {
        UUID conversationId = UUID.randomUUID();
        UUID profileJobId = UUID.randomUUID();
        UUID weatherJobId = UUID.randomUUID();
        ContextEnvelope envelope = envelope(
                conversationId,
                profileJobId,
                weatherJobId);

        String facts = factsBlock(projector.project(
                envelope,
                profileJobId,
                scope("RESUME_OR_PROFILE_GENERATION")));

        assertThat(facts).contains("name=冯建松", "style=轻松");
        assertThat(facts).doesNotContain("city=北京", "location=北京");
    }

    @Test
    void weatherTaskReceivesWeatherFactsButNotProfileOnlyFacts() {
        UUID conversationId = UUID.randomUUID();
        UUID profileJobId = UUID.randomUUID();
        UUID weatherJobId = UUID.randomUUID();
        ContextEnvelope envelope = envelope(
                conversationId,
                profileJobId,
                weatherJobId);

        String facts = factsBlock(projector.project(
                envelope,
                weatherJobId,
                scope("WEATHER_QUERY")));

        assertThat(facts).contains("name=冯建松", "city=北京", "location=北京");
        assertThat(facts).doesNotContain("style=轻松");
    }

    /**
     * Creates an envelope containing both stable and task-specific facts.
     */
    private ContextEnvelope envelope(
            UUID conversationId,
            UUID profileJobId,
            UUID weatherJobId) {
        return new ContextEnvelope(
                new ContextConversation(conversationId, "mixed turn", null),
                List.of(new ContextMessage(
                        "USER",
                        "TEXT",
                        "我叫冯建松，帮我写介绍，也查北京天气")),
                List.of(
                        fact(conversationId, "CONVERSATION", "name", "冯建松"),
                        fact(conversationId, "CONVERSATION", "city", "北京"),
                        fact(conversationId, "JOB:" + profileJobId, "style", "轻松"),
                        fact(conversationId, "JOB:" + weatherJobId, "location", "北京")),
                List.of(),
                List.of());
    }

    /**
     * Creates one structured fact for test context projection.
     */
    private ContextFact fact(
            UUID conversationId,
            String scope,
            String key,
            String value) {
        Instant now = Instant.now();
        return new ContextFact(
                UUID.randomUUID(),
                conversationId,
                UUID.randomUUID(),
                "test",
                scope,
                key,
                value,
                0.95,
                now,
                now);
    }

    /**
     * Creates a task scope for projection tests.
     */
    private TaskIntentScope scope(String taskType) {
        return new TaskIntentScope(
                UUID.randomUUID().toString(),
                taskType,
                "",
                "",
                "",
                List.of(),
                "LOW",
                "{}",
                List.of(),
                true);
    }

    /**
     * Extracts the structured fact block from projected context blocks.
     */
    private String factsBlock(List<ContextBlock> blocks) {
        return blocks.stream()
                .filter(block -> block.title().equals("Task-Scoped Structured Facts"))
                .findFirst()
                .orElseThrow()
                .content();
    }
}
