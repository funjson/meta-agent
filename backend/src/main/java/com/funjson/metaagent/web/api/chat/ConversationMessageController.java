package com.funjson.metaagent.web.api.chat;

import com.funjson.metaagent.control.api.ChatTurnRequest;
import com.funjson.metaagent.control.api.ChatTurnResult;
import com.funjson.metaagent.control.application.ControlKernel;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Conversation 消息 HTTP Adapter。
 *
 * <p>路径表达 Conversation 资源语义，实际控制处理委托给 ControlKernel。</p>
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationMessageController {

    private final ControlKernel controlKernel;

    /**
     * 创建 Conversation 消息 Controller。
     *
     * @param controlKernel Control Kernel
     */
    public ConversationMessageController(ControlKernel controlKernel) {
        this.controlKernel = controlKernel;
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
}
