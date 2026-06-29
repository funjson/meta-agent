package com.funjson.metaagent.file.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.file.domain.ConversationFile;

/**
 * Conversation 文件附件持久化端口。
 */
public interface ConversationFileStore {

    /** 插入文件元数据。 */
    void insert(ConversationFile file);

    /** @return Conversation 下的文件列表 */
    List<ConversationFile> findByConversation(UUID conversationId);

    /** @return 指定文件 */
    Optional<ConversationFile> findById(
            UUID conversationId,
            UUID fileId);

    /** @return 指定文件名的最近文件 */
    Optional<ConversationFile> findLatestByName(
            UUID conversationId,
            String fileName);
}
