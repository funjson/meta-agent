package com.funjson.metaagent.web.api.chat;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.control.api.ChatTurnRequest;
import com.funjson.metaagent.control.api.ChatTurnResult;
import com.funjson.metaagent.control.application.ControlKernel;
import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.conversation.api.MessageView;
import com.funjson.metaagent.conversation.application.ConversationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Conversation 消息 HTTP Adapter。
 *
 * <p>路径表达 Conversation 资源语义，实际控制处理委托给 ControlKernel。</p>
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationMessageController {

    private final ControlKernel controlKernel;
    private final ConversationService conversationService;
    private final long streamTimeoutMs;
    private final long streamPollIntervalMs;

    /**
     * 创建 Conversation 消息 Controller。
     *
     * @param controlKernel Control Kernel
     */
    public ConversationMessageController(
            ControlKernel controlKernel,
            ConversationService conversationService,
            @Value("${meta-agent.chat.stream-timeout-ms:120000}")
            long streamTimeoutMs,
            @Value("${meta-agent.chat.stream-poll-interval-ms:350}")
            long streamPollIntervalMs) {
        this.controlKernel = controlKernel;
        this.conversationService = conversationService;
        this.streamTimeoutMs = streamTimeoutMs;
        this.streamPollIntervalMs = streamPollIntervalMs;
    }

    /**
     * 发送一轮聊天消息。
     *
     * @param conversationId Conversation ID
     * @param idempotencyKey 幂等键
     * @param request 聊天请求
     * @return 本轮结果
     */
    @PostMapping("/{conversationId}/messages")
    public ChatTurnResult send(
            @PathVariable UUID conversationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ChatTurnRequest request) {
        return controlKernel.send(
                conversationId,
                idempotencyKey,
                request);
    }

    /**
     * Sends a chat turn and streams user-visible state transitions back to the
     * browser.
     *
     * <p>v0.1 keeps Job execution in the durable background worker and streams
     * the final assistant message after it is committed. This endpoint gives the
     * UI a stable transport contract today, while leaving room for provider
     * token streaming later without changing the browser interaction model.</p>
     *
     * @param conversationId Conversation ID
     * @param idempotencyKey idempotency key
     * @param request chat request
     * @return SSE emitter
     */
    @PostMapping(
            value = "/{conversationId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendStream(
            @PathVariable UUID conversationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ChatTurnRequest request) {
        SseEmitter emitter = new SseEmitter(streamTimeoutMs + 5_000);
        Thread.startVirtualThread(() -> streamTurn(
                emitter,
                conversationId,
                idempotencyKey,
                request));
        return emitter;
    }

    /**
     * Owns the short-lived SSE loop for one user turn.
     */
    private void streamTurn(
            SseEmitter emitter,
            UUID conversationId,
            String idempotencyKey,
            ChatTurnRequest request) {
        int initialMessageCount = conversationService.get(conversationId)
                .messages()
                .size();
        try {
            emit(emitter, "stream_mode", new StreamMode("sse-replay"));
            ChatTurnResult result = controlKernel.send(
                    conversationId,
                    idempotencyKey,
                    request);
            emit(emitter, "turn", result);

            MessageView assistant = firstAssistantAfter(
                    result.conversation(),
                    initialMessageCount);
            if (assistant == null) {
                assistant = waitForAssistant(conversationId, initialMessageCount);
            }
            if (assistant != null) {
                streamAssistantMessage(emitter, assistant);
                emit(emitter, "conversation", conversationService.get(conversationId));
            }
            emit(emitter, "done", new DoneEvent(assistant != null));
            emitter.complete();
        } catch (RuntimeException | IOException exception) {
            try {
                emit(emitter, "error", new ErrorEvent(exception.getMessage()));
            } catch (IOException ignored) {
                // The browser may already have closed the stream; complete with
                // the original failure so server-side diagnostics still see it.
            }
            emitter.completeWithError(exception);
        }
    }

    /**
     * Waits for the background worker to append a user-visible assistant
     * message.
     */
    private MessageView waitForAssistant(
            UUID conversationId,
            int initialMessageCount) {
        long deadline = System.currentTimeMillis() + streamTimeoutMs;
        while (System.currentTimeMillis() < deadline) {
            ConversationView conversation = conversationService.get(conversationId);
            MessageView assistant = firstAssistantAfter(
                    conversation,
                    initialMessageCount);
            if (assistant != null) {
                return assistant;
            }
            sleep(streamPollIntervalMs);
        }
        return null;
    }

    /**
     * Finds the first assistant message created after the triggering user
     * message count.
     */
    private MessageView firstAssistantAfter(
            ConversationView conversation,
            int initialMessageCount) {
        List<MessageView> messages = conversation.messages();
        for (int index = initialMessageCount; index < messages.size(); index++) {
            MessageView message = messages.get(index);
            if ("ASSISTANT".equals(message.role())) {
                return message;
            }
        }
        return null;
    }

    /**
     * Emits the final stored message as deterministic chunks.
     */
    private void streamAssistantMessage(
            SseEmitter emitter,
            MessageView message) throws IOException {
        emit(emitter, "assistant_start", new AssistantStart(
                message.id(),
                message.messageType(),
                message.jobId(),
                message.taskRunId(),
                message.createdAt().toString()));
        for (String chunk : chunks(message.content())) {
            emit(emitter, "assistant_delta", new AssistantDelta(chunk));
            sleep(18);
        }
        emit(emitter, "assistant_done", message);
    }

    /**
     * Splits text into small visual chunks without pretending to be provider
     * token streaming.
     */
    private List<String> chunks(String content) {
        if (content == null || content.isBlank()) {
            return List.of("");
        }
        java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
        int index = 0;
        while (index < content.length()) {
            int next = Math.min(content.length(), index + 18);
            chunks.add(content.substring(index, next));
            index = next;
        }
        return List.copyOf(chunks);
    }

    /**
     * Sends an SSE event.
     */
    private void emit(
            SseEmitter emitter,
            String name,
            Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    /**
     * Sleeps without leaking checked exceptions into stream control flow.
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(10, millis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chat stream interrupted", exception);
        }
    }

    /**
     * Stream mode metadata.
     */
    private record StreamMode(String mode) {
    }

    /**
     * Assistant stream start event.
     */
    private record AssistantStart(
            UUID id,
            String messageType,
            UUID jobId,
            UUID taskRunId,
            String createdAt) {
    }

    /**
     * Assistant stream delta event.
     */
    private record AssistantDelta(String content) {
    }

    /**
     * Stream completion event.
     */
    private record DoneEvent(boolean assistantMessageEmitted) {
    }

    /**
     * Stream error event.
     */
    private record ErrorEvent(String message) {
    }
}
