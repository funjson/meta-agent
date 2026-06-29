package com.funjson.metaagent.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.clarification.application.ClarificationService;
import com.funjson.metaagent.context.application.ContextAssembler;
import com.funjson.metaagent.context.application.port.out.ConversationFactStore;
import com.funjson.metaagent.context.domain.ContextFact;
import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.conversation.api.MessageView;
import com.funjson.metaagent.conversation.application.port.out.ConversationStore;
import org.junit.jupiter.api.Test;

/**
 * Verifies Conversation-level structured facts enter prompt views.
 */
class ContextAssemblerFactTest {

    @Test
    void rendersConversationFactsIntoIntentPromptView() {
        UUID conversationId = UUID.randomUUID();
        ConversationStore conversationStore = mock(ConversationStore.class);
        ClarificationService clarificationService = mock(ClarificationService.class);
        ConversationFactStore factStore = mock(ConversationFactStore.class);
        Instant now = Instant.parse("2026-06-28T00:00:00Z");
        when(conversationStore.findConversation(conversationId)).thenReturn(
                Optional.of(new ConversationView(
                        conversationId,
                        "general-agent",
                        "测试会话",
                        "ACTIVE",
                        "fake",
                        null,
                        1,
                        now,
                        now,
                        List.of())));
        when(conversationStore.findMessages(conversationId)).thenReturn(List.of(
                new MessageView(
                        UUID.randomUUID(),
                        "USER",
                        "TEXT",
                        "你好",
                        null,
                        null,
                        now)));
        when(clarificationService.findOpenByConversation(conversationId))
                .thenReturn(List.of());
        when(clarificationService.findRecentResolvedByConversation(
                conversationId,
                20)).thenReturn(List.of());
        when(factStore.findActiveByConversation(conversationId)).thenReturn(
                List.of(new ContextFact(
                        UUID.randomUUID(),
                        conversationId,
                        UUID.randomUUID(),
                        "CLARIFICATION_ANSWER",
                        "CONVERSATION",
                        "name",
                        "冯建松",
                        0.86,
                        now,
                        now)));
        ContextAssembler assembler = new ContextAssembler(
                conversationStore,
                clarificationService,
                factStore);

        String promptView = assembler.intentPromptView(
                assembler.envelope(conversationId));

        assertThat(promptView)
                .contains("Conversation 级结构化事实")
                .contains("name=冯建松")
                .contains("CLARIFICATION_ANSWER");
    }
}
