package com.funjson.metaagent.context.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for Conversation facts.
 */
@Mapper
public interface ConversationFactPersistenceMapper {

    /** Upserts one fact row. */
    int upsert(
            @Param("id") UUID id,
            @Param("conversationId") UUID conversationId,
            @Param("sourceMessageId") UUID sourceMessageId,
            @Param("sourceType") String sourceType,
            @Param("scope") String scope,
            @Param("key") String key,
            @Param("value") String value,
            @Param("confidence") double confidence);

    /** Finds active facts for a conversation. */
    List<Map<String, Object>> findActiveByConversation(
            @Param("conversationId") UUID conversationId);
}
