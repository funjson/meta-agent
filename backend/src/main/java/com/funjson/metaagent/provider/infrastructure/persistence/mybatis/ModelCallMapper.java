package com.funjson.metaagent.provider.infrastructure.persistence.mybatis;

import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 写入模型调用审计记录。
 */
@Mapper
public interface ModelCallMapper {

    /**
     * 插入模型调用审计。
     *
     * @param id 调用 ID
     * @param taskRunId TaskRun ID
     * @param loopNodeId LoopNode ID
     * @param provider Provider
     * @param model 模型
     * @param fingerprint 请求指纹
     * @param promptId Prompt ID
     * @param promptVersion Prompt 版本
     * @param promptHash Prompt 内容哈希
     * @param status 调用状态
     * @param promptTokens 输入 Token
     * @param completionTokens 输出 Token
     * @param latencyMs 延迟
     * @param errorCode 错误码
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO model_call (
                id, task_run_id, loop_node_id, provider, model,
                request_fingerprint, prompt_id, prompt_version, prompt_hash,
                status, prompt_tokens, completion_tokens, latency_ms, error_code
            ) VALUES (
                #{id,typeHandler=com.funjson.metaagent.common.persistence.mybatis.UuidBinaryTypeHandler},
                #{taskRunId,jdbcType=BINARY,typeHandler=com.funjson.metaagent.common.persistence.mybatis.UuidBinaryTypeHandler},
                #{loopNodeId,jdbcType=BINARY,typeHandler=com.funjson.metaagent.common.persistence.mybatis.UuidBinaryTypeHandler},
                #{provider}, #{model}, #{fingerprint}, #{promptId}, #{promptVersion},
                #{promptHash}, #{status}, #{promptTokens}, #{completionTokens},
                #{latencyMs}, #{errorCode}
            )
            """)
    int insert(
            @Param("id") UUID id,
            @Param("taskRunId") UUID taskRunId,
            @Param("loopNodeId") UUID loopNodeId,
            @Param("provider") String provider,
            @Param("model") String model,
            @Param("fingerprint") String fingerprint,
            @Param("promptId") String promptId,
            @Param("promptVersion") String promptVersion,
            @Param("promptHash") String promptHash,
            @Param("status") String status,
            @Param("promptTokens") Integer promptTokens,
            @Param("completionTokens") Integer completionTokens,
            @Param("latencyMs") long latencyMs,
            @Param("errorCode") String errorCode);
}
