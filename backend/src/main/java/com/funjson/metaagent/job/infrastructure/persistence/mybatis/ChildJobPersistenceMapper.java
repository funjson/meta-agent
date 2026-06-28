package com.funjson.metaagent.job.infrastructure.persistence.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;
import java.util.UUID;

/**
 * Child Job 物化事务的 MyBatis Mapper。
 */
@Mapper
public interface ChildJobPersistenceMapper {

    /** @return 已存在 Child Job ID */
    String findChildJobId(@Param("idempotencyKey") String idempotencyKey);

    /** @return 加锁父 Job 行 */
    Map<String, Object> lockParent(@Param("parentJobId") UUID parentJobId);

    /** @return 直接子 Job 数量 */
    long countDirectChildren(@Param("parentJobId") UUID parentJobId);

    /** @return 整树 Job 数量 */
    long countTreeJobs(@Param("rootJobId") UUID rootJobId);

    /** @return 插入行数 */
    int insertDerivation(
            @Param("id") UUID id,
            @Param("parentJobId") UUID parentJobId,
            @Param("childJobId") UUID childJobId,
            @Param("originTaskRunId") UUID originTaskRunId,
            @Param("originLoopNodeId") UUID originLoopNodeId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("sourceSkillId") String sourceSkillId,
            @Param("sourceSkillVersion") Integer sourceSkillVersion,
            @Param("requestJson") String requestJson);

    /** @return 更新行数 */
    int bindOriginLoopNode(
            @Param("originLoopNodeId") UUID originLoopNodeId,
            @Param("childJobId") UUID childJobId);

    /** @return Child Job 完成快照 */
    Map<String, Object> lockCompletion(
            @Param("childJobId") UUID childJobId);

    /** @return Child Job 结果摘要 */
    String summarizeChildJob(@Param("childJobId") UUID childJobId);

    /** @return Child Job Evidence 数量 */
    int countChildJobEvidence(@Param("childJobId") UUID childJobId);

    /** @return 更新行数 */
    int completeDerivation(
            @Param("childJobId") UUID childJobId,
            @Param("outcomeJson") String outcomeJson);

    /** @return 更新行数 */
    int clearOriginLoopNode(
            @Param("originLoopNodeId") UUID originLoopNodeId,
            @Param("childJobId") UUID childJobId);
}
