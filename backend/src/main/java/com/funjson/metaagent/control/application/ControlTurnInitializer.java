package com.funjson.metaagent.control.application;

import java.util.UUID;

import com.funjson.metaagent.context.application.ContextAssembler;
import com.funjson.metaagent.context.domain.ContextEnvelope;
import com.funjson.metaagent.control.api.ChatTurnRequest;
import com.funjson.metaagent.control.api.ControlDecisionView;
import com.funjson.metaagent.control.application.port.out.ControlTurnStore;
import com.funjson.metaagent.control.domain.ControlTurn;
import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.conversation.api.MessageView;
import com.funjson.metaagent.conversation.application.port.out.ConversationStore;
import com.funjson.metaagent.intent.application.TurnRouter;
import com.funjson.metaagent.intent.domain.PendingInteractionCandidate;
import com.funjson.metaagent.intent.domain.TurnRoutingPlan;
import com.funjson.metaagent.intent.domain.TurnRoutingRequest;
import com.funjson.metaagent.job.api.JobView;
import com.funjson.metaagent.job.application.JobService;
import com.funjson.metaagent.provider.application.port.out.ProviderSecretPort;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initializes a single auditable Control turn.
 *
 * <p>This service owns the short transaction that writes the user message and
 * ControlTurn. It delegates routing to {@link TurnRouter} and action execution
 * to {@link ControlActionExecutor}, keeping Control initialization from
 * accumulating intent, clarification and Job creation details.</p>
 */
@Service
public class ControlTurnInitializer {

    private final ConversationStore conversationStore;
    private final ControlTurnStore controlTurnStore;
    private final JobService jobService;
    private final ContextAssembler contextAssembler;
    private final TurnRouter turnRouter;
    private final ControlActionExecutor actionExecutor;
    private final ProviderSecretPort secretStore;

    /**
     * Creates a Control turn initializer.
     *
     * @param conversationStore Conversation Store Port
     * @param controlTurnStore ControlTurn Store Port
     * @param jobService Job Service
     * @param contextAssembler context assembler
     * @param turnRouter user turn router
     * @param actionExecutor Control action executor
     * @param secretStore Provider Secret Store
     */
    public ControlTurnInitializer(
            ConversationStore conversationStore,
            ControlTurnStore controlTurnStore,
            JobService jobService,
            ContextAssembler contextAssembler,
            TurnRouter turnRouter,
            ControlActionExecutor actionExecutor,
            ProviderSecretPort secretStore) {
        this.conversationStore = conversationStore;
        this.controlTurnStore = controlTurnStore;
        this.jobService = jobService;
        this.contextAssembler = contextAssembler;
        this.turnRouter = turnRouter;
        this.actionExecutor = actionExecutor;
        this.secretStore = secretStore;
    }

    /**
     * Initializes one auditable ControlTurn.
     *
     * @param conversationId Conversation ID
     * @param idempotencyKey idempotency key
     * @param request chat request
     * @return initialization result
     */
    @Transactional
    public ControlTurnInitialization initialize(
            UUID conversationId,
            String idempotencyKey,
            ChatTurnRequest request) {
        ConversationView conversation = conversationStore.findConversation(
                        conversationId)
                .orElseThrow(() -> new RuntimeStateException(
                        "CONVERSATION_NOT_FOUND",
                        "Conversation not found: " + conversationId));

        // Idempotent retry is anchored at ControlTurn; the user message,
        // decision and Job must not be written twice.
        var existing = controlTurnStore.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existingInitialization(conversation, existing.get());
        }

        UUID userMessageId = UUID.randomUUID();
        UUID controlTurnId = UUID.randomUUID();
        String content = request.content().trim();
        conversationStore.insertMessage(
                userMessageId,
                conversationId,
                "USER",
                "TEXT",
                content,
                idempotencyKey,
                null,
                null);
        controlTurnStore.insertTurn(
                controlTurnId,
                conversationId,
                userMessageId,
                idempotencyKey);

        ContextEnvelope envelope = contextAssembler.envelope(conversationId);
        boolean modelClassificationAllowed = allowsModelClassification(
                request.providerId(),
                conversation.defaultProviderId());
        TurnRoutingPlan plan = turnRouter.route(new TurnRoutingRequest(
                content,
                contextAssembler.intentPromptView(envelope),
                conversation.activeJobId(),
                envelope.openClarifications().stream()
                        .map(this::toPendingCandidate)
                        .toList(),
                modelClassificationAllowed));
        return actionExecutor.execute(
                new ControlTurnExecutionContext(
                        conversation,
                        controlTurnId,
                        userMessageId,
                        idempotencyKey,
                        content,
                        request,
                        envelope,
                        modelClassificationAllowed),
                plan);
    }

    /**
     * Returns the prior initialization result for an idempotent retry.
     */
    private ControlTurnInitialization existingInitialization(
            ConversationView conversation,
            ControlTurn turn) {
        MessageView userMessage = conversationStore.findMessageById(
                        turn.sourceMessageId())
                .orElseThrow(() -> new IllegalStateException(
                        "ControlTurn source message is missing"));
        ControlDecisionView decision = controlTurnStore.findDecision(turn.id())
                .orElseThrow(() -> new IllegalStateException(
                        "ControlDecision is missing"));
        JobView job = turn.jobId() == null ? null : jobService.get(turn.jobId());
        return new ControlTurnInitialization(
                turn.id(),
                conversation,
                userMessage,
                decision,
                job,
                null);
    }

    /**
     * Converts an open clarification into an Intent-layer pending candidate.
     */
    private PendingInteractionCandidate toPendingCandidate(
            com.funjson.metaagent.clarification.domain.ClarificationRequest request) {
        return new PendingInteractionCandidate(
                request.id(),
                "CLARIFICATION",
                request.jobId(),
                request.taskId(),
                request.taskRunId(),
                request.loopNodeId(),
                request.question(),
                request.blockingSummary(),
                request.contractJson());
    }

    /**
     * Determines whether model-backed routing is allowed for this turn.
     *
     * @param requested request-level provider
     * @param conversationDefault conversation default provider
     * @return true when non-fake mode and a provider secret is configured
     */
    private boolean allowsModelClassification(
            String requested,
            String conversationDefault) {
        String candidate = requested == null || requested.isBlank()
                ? conversationDefault
                : requested.trim();
        return !"fake".equals(candidate) && secretStore.configured();
    }
}
