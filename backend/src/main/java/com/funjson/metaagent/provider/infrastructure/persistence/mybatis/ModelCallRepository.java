package com.funjson.metaagent.provider.infrastructure.persistence.mybatis;

import java.util.UUID;

import org.springframework.stereotype.Repository;

/**
 * 适配模型调用审计与 MyBatis Mapper。
 */
@Repository
public class ModelCallRepository {

    private final ModelCallMapper mapper;

    /**
     * 创建模型调用 Repository。
     *
     * @param mapper 模型调用 Mapper
     */
    public ModelCallRepository(ModelCallMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 保存一次模型调用。
     *
     * @param id 调用 ID
     * @param taskRunId TaskRun ID
     * @param loopNodeId LoopNode ID
     * @param provider Provider
     * @param model 模型
     * @param fingerprint 请求指纹
     * @param promptId Prompt ID
     * @param promptVersion Prompt 版本
     * @param promptHash Prompt 哈希
     * @param status 调用状态
     * @param promptTokens 输入 Token
     * @param completionTokens 输出 Token
     * @param latencyMs 延迟
     * @param errorCode 错误码
     */
    public void insert(
            UUID id,
            UUID taskRunId,
            UUID loopNodeId,
            String provider,
            String model,
            String fingerprint,
            String promptId,
            String promptVersion,
            String promptHash,
            String status,
            Integer promptTokens,
            Integer completionTokens,
            long latencyMs,
            String errorCode) {
        mapper.insert(
                id,
                taskRunId,
                loopNodeId,
                provider,
                model,
                fingerprint,
                promptId,
                promptVersion,
                promptHash,
                status,
                promptTokens,
                completionTokens,
                latencyMs,
                errorCode);
    }
}
