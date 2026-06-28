package com.funjson.metaagent.web.api.conversation;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.conversation.api.ConversationView;
import com.funjson.metaagent.conversation.api.CreateConversationRequest;
import com.funjson.metaagent.conversation.application.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供 Conversation 资源 HTTP API。
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 创建 Conversation Controller。
     *
     * @param conversationService Conversation Service
     */
    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * 创建 Conversation。
     *
     * @param request 创建请求
     * @return 新 Conversation
     */
    @PostMapping
    public ResponseEntity<ConversationView> create(
            @Valid @RequestBody CreateConversationRequest request) {
        ConversationView created = conversationService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/conversations/" + created.id()))
                .body(created);
    }

    /**
     * 查询 Conversation。
     *
     * @param conversationId Conversation ID
     * @return Conversation
     */
    @GetMapping("/{conversationId}")
    public ConversationView get(@PathVariable UUID conversationId) {
        return conversationService.get(conversationId);
    }

    /**
     * 查询 Conversation 历史列表。
     *
     * @return Conversation 摘要列表
     */
    @GetMapping
    public List<ConversationView> list() {
        return conversationService.list();
    }

}
