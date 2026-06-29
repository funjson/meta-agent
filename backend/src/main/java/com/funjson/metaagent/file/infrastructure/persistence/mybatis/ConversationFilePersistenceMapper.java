package com.funjson.metaagent.file.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Conversation 文件附件 MyBatis Mapper。
 */
@Mapper
public interface ConversationFilePersistenceMapper {

    /** @return 插入行数 */
    int insert(
            @Param("id") UUID id,
            @Param("conversationId") UUID conversationId,
            @Param("fileName") String fileName,
            @Param("storagePath") String storagePath,
            @Param("contentType") String contentType,
            @Param("sizeBytes") long sizeBytes,
            @Param("checksumSha256") String checksumSha256,
            @Param("status") String status);

    /** @return Conversation 下文件行 */
    List<Map<String, Object>> findByConversation(
            @Param("conversationId") UUID conversationId);

    /** @return 指定文件行 */
    Map<String, Object> findById(
            @Param("conversationId") UUID conversationId,
            @Param("fileId") UUID fileId);

    /** @return 文件名最近匹配行 */
    Map<String, Object> findLatestByName(
            @Param("conversationId") UUID conversationId,
            @Param("fileName") String fileName);
}
