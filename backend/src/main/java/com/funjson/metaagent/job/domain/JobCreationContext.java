package com.funjson.metaagent.job.domain;

import java.util.UUID;

/**
 * 描述 Job 从聊天入口创建时的来源上下文。
 *
 * @param agentProfileId AgentProfile ID
 * @param conversationId Conversation ID
 * @param sourceMessageId 来源消息 ID
 * @param parentJobId 父 Job ID
 * @param rootJobId 根 Job ID；根 Job 创建时为空，由持久化层使用自身 ID
 * @param recursionDepth Job 树递归深度
 * @param templateId TaskGraphTemplate ID
 * @param templateVersion TaskGraphTemplate 版本
 * @param subagentProfileId SubagentProfile ID
 * @param subagentProfileVersion SubagentProfile 版本
 */
public record JobCreationContext(
        String agentProfileId,
        UUID conversationId,
        UUID sourceMessageId,
        UUID parentJobId,
        UUID rootJobId,
        int recursionDepth,
        UUID templateId,
        Integer templateVersion,
        String subagentProfileId,
        Integer subagentProfileVersion) {

    /**
     * 创建聊天入口的根 Job 上下文。
     *
     * @param agentProfileId AgentProfile ID
     * @param conversationId Conversation ID
     * @param sourceMessageId 来源消息 ID
     * @param templateId 可选模板 ID
     * @param templateVersion 可选模板版本
     * @return 根 Job 上下文
     */
    public static JobCreationContext root(
            String agentProfileId,
            UUID conversationId,
            UUID sourceMessageId,
            UUID templateId,
            Integer templateVersion) {
        return new JobCreationContext(
                agentProfileId,
                conversationId,
                sourceMessageId,
                null,
                null,
                0,
                templateId,
                templateVersion,
                null,
                null);
    }

    /**
     * 创建不关联聊天入口的兼容上下文。
     *
     * @return 空来源上下文
     */
    public static JobCreationContext standalone() {
        return new JobCreationContext(
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null);
    }
}
