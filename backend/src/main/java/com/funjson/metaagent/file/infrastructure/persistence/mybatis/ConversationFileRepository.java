package com.funjson.metaagent.file.infrastructure.persistence.mybatis;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.file.application.port.out.ConversationFileStore;
import com.funjson.metaagent.file.domain.ConversationFile;
import org.springframework.stereotype.Repository;

/**
 * ConversationFileStore 的 MyBatis 适配器。
 */
@Repository
public class ConversationFileRepository implements ConversationFileStore {

    private final ConversationFilePersistenceMapper mapper;

    /**
     * 创建文件 Repository。
     *
     * @param mapper MyBatis Mapper
     */
    public ConversationFileRepository(
            ConversationFilePersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ConversationFile file) {
        mapper.insert(
                file.id(),
                file.conversationId(),
                file.fileName(),
                file.storagePath(),
                file.contentType(),
                file.sizeBytes(),
                file.checksumSha256(),
                file.status());
    }

    @Override
    public List<ConversationFile> findByConversation(UUID conversationId) {
        return mapper.findByConversation(conversationId)
                .stream()
                .map(this::map)
                .toList();
    }

    @Override
    public Optional<ConversationFile> findById(
            UUID conversationId,
            UUID fileId) {
        return Optional.ofNullable(mapper.findById(conversationId, fileId))
                .map(this::map);
    }

    @Override
    public Optional<ConversationFile> findLatestByName(
            UUID conversationId,
            String fileName) {
        return Optional.ofNullable(
                        mapper.findLatestByName(conversationId, fileName))
                .map(this::map);
    }

    /**
     * 转换数据库行。
     */
    private ConversationFile map(Map<String, Object> row) {
        return new ConversationFile(
                UUID.fromString(text(row, "id")),
                UUID.fromString(text(row, "conversationId")),
                text(row, "fileName"),
                text(row, "storagePath"),
                text(row, "contentType"),
                ((Number) row.get("sizeBytes")).longValue(),
                text(row, "checksumSha256"),
                text(row, "status"),
                instant(row.get("createdAt")));
    }

    /**
     * 读取字符串列。
     */
    private String text(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }

    /**
     * 转换数据库时间。
     */
    private Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.atZone(java.time.ZoneOffset.UTC).toInstant();
        }
        throw new IllegalArgumentException(
                "Unsupported timestamp value: " + value);
    }
}
