package com.funjson.metaagent.web.api.file;

import java.util.List;
import java.util.UUID;

import com.funjson.metaagent.file.api.ConversationFileView;
import com.funjson.metaagent.file.application.FileAttachmentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Conversation 文件附件 HTTP Adapter。
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationFileController {

    private final FileAttachmentService fileAttachmentService;

    /**
     * 创建文件 Controller。
     *
     * @param fileAttachmentService 文件附件服务
     */
    public ConversationFileController(
            FileAttachmentService fileAttachmentService) {
        this.fileAttachmentService = fileAttachmentService;
    }

    /**
     * 上传文件到指定 Conversation。
     *
     * @param conversationId Conversation ID
     * @param file Multipart 文件
     * @return 文件视图
     */
    @PostMapping(
            value = "/{conversationId}/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ConversationFileView upload(
            @PathVariable UUID conversationId,
            @RequestPart("file") MultipartFile file) {
        return fileAttachmentService.upload(conversationId, file);
    }

    /**
     * 查询 Conversation 文件列表。
     *
     * @param conversationId Conversation ID
     * @return 文件列表
     */
    @GetMapping("/{conversationId}/files")
    public List<ConversationFileView> list(
            @PathVariable UUID conversationId) {
        return fileAttachmentService.list(conversationId);
    }
}
