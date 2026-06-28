package com.funjson.metaagent.clarification.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.clarification.application.port.out.ClarificationStore;
import com.funjson.metaagent.clarification.domain.ClarificationPolicy;
import com.funjson.metaagent.clarification.domain.ClarificationRequest;
import com.funjson.metaagent.clarification.domain.ClarificationRequestDraft;
import com.funjson.metaagent.clarification.domain.ClarificationResolution;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理澄清请求的创建、去重和查询。
 */
@Service
public class ClarificationService {

    private final ClarificationStore store;
    private final ClarificationPolicy policy;
    private final ObjectMapper objectMapper;

    /**
     * 创建澄清应用服务。
     *
     * @param store 澄清持久化端口
     * @param policy 澄清策略
     * @param objectMapper JSON Mapper
     */
    public ClarificationService(
            ClarificationStore store,
            ClarificationPolicy policy,
            ObjectMapper objectMapper) {
        this.store = store;
        this.policy = policy;
        this.objectMapper = objectMapper;
    }

    /**
     * 为 TaskGraph 等待节点创建结构化澄清请求。
     *
     * @param conversationId Conversation ID
     * @param jobId Job ID
     * @param taskId 等待 Task ID
     * @param draft 澄清草稿
     * @return 澄清请求 ID
     */
    @Transactional
    public UUID openForTaskGraph(
            UUID conversationId,
            UUID jobId,
            UUID taskId,
            ClarificationRequestDraft draft) {
        return open(
                conversationId,
                jobId,
                taskId,
                null,
                null,
                draft);
    }

    /**
     * 创建任意执行层级的澄清请求，并与原始恢复点绑定。
     *
     * @param conversationId Conversation ID
     * @param jobId Job ID
     * @param taskId 可选 Task ID
     * @param taskRunId 可选 TaskRun ID
     * @param loopNodeId 可选 LoopNode ID
     * @param draft 澄清草稿
     * @return 澄清请求 ID
     */
    @Transactional
    public UUID open(
            UUID conversationId,
            UUID jobId,
            UUID taskId,
            UUID taskRunId,
            UUID loopNodeId,
            ClarificationRequestDraft draft) {
        List<ClarificationRequest> existing = store.findOpenBySource(
                jobId,
                taskId,
                taskRunId,
                loopNodeId);
        policy.requireAllowed(existing, draft);
        UUID requestId = UUID.randomUUID();
        store.insert(
                requestId,
                conversationId,
                jobId,
                taskId,
                taskRunId,
                loopNodeId,
                draft);
        return requestId;
    }

    /**
     * 查询 Job 下当前打开的澄清请求。
     *
     * @param jobId Job ID
     * @return 打开的澄清请求
     */
    @Transactional(readOnly = true)
    public Optional<ClarificationRequest> findOpenByJob(UUID jobId) {
        return store.findOpenByJobId(jobId);
    }

    /**
     * 查询 Conversation 当前全部打开的澄清请求。
     *
     * <p>用户的新消息不再默认绑定到最近请求；Control/Intent 层会把这些请求作为
     * 候选集进行匹配或消歧。</p>
     *
     * @param conversationId Conversation ID
     * @return 打开的澄清候选列表
     */
    @Transactional(readOnly = true)
    public List<ClarificationRequest> findOpenByConversation(
            UUID conversationId) {
        return store.findOpenByConversationId(conversationId);
    }

    /**
     * 查询 Conversation 最近已经解决的澄清请求。
     *
     * <p>这些记录会进入 Conversation 级上下文，供后续 PendingInteractionRouter
     * 复用已抽取事实，避免同类任务重复追问。</p>
     *
     * @param conversationId Conversation ID
     * @param limit 最大返回数量
     * @return 最近解决的澄清记录
     */
    @Transactional(readOnly = true)
    public List<ClarificationRequest> findRecentResolvedByConversation(
            UUID conversationId,
            int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return store.findRecentResolvedByConversationId(
                conversationId,
                safeLimit);
    }

    /**
     * 记录用户对澄清问题的回答。
     *
     * @param requestId 澄清请求 ID
     * @param messageId 用户回答消息 ID
     * @param answer 回答内容
     */
    @Transactional
    public void answer(
            UUID requestId,
            UUID messageId,
            String answer) {
        store.answer(
                requestId,
                messageId,
                answer);
    }

    /**
     * 记录一轮部分回答并保持澄清请求打开。
     *
     * <p>当结构化完整性评估判断仍缺少必填字段时，不应恢复 Job/TaskRun；
     * 此时把已抽取事实写入 resolution_json 作为“部分决议快照”，下一轮回答会
     * 与这些事实合并后再次判断。</p>
     *
     * @param requestId 澄清请求 ID
     * @param messageId 用户回答消息 ID
     * @param answer 当前回答文本
     * @param partialResolution 部分决议快照
     */
    @Transactional
    public void recordPartialAnswer(
            UUID requestId,
            UUID messageId,
            String answer,
            ClarificationResolution partialResolution) {
        store.recordPartialAnswer(
                requestId,
                messageId,
                answer,
                json(partialResolution));
    }

    /**
     * 标记澄清请求已经恢复到原执行点。
     *
     * @param resolution 恢复决议
     */
    @Transactional
    public void resolve(ClarificationResolution resolution) {
        store.resolve(
                resolution.clarificationRequestId(),
                json(resolution));
    }

    /** 序列化恢复决议。 */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize clarification resolution",
                    exception);
        }
    }
}
