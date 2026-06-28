package com.funjson.metaagent.provider;

import java.util.UUID;

import com.funjson.metaagent.provider.infrastructure.persistence.mybatis.ModelCallRepository;

/**
 * 测试使用的无持久化模型调用审计仓库。
 */
class NoOpModelCallRepository extends ModelCallRepository {

    /**
     * 创建无操作仓库。
     */
    NoOpModelCallRepository() {
        super(null);
    }

    /**
     * 忽略测试中的审计写入。
     *
     * @param id 调用 ID
     * @param taskRunId TaskRun ID
     * @param loopNodeId LoopNode ID
     * @param provider Provider
     * @param model 模型
     * @param fingerprint 指纹
     * @param promptId Prompt ID
     * @param promptVersion Prompt 版本
     * @param promptHash Prompt 哈希
     * @param status 状态
     * @param promptTokens 输入 Token
     * @param completionTokens 输出 Token
     * @param latencyMs 延迟
     * @param errorCode 错误码
     */
    @Override
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
        // 单元测试只验证 Provider 输出，不验证 MyBatis 写入。
    }
}
