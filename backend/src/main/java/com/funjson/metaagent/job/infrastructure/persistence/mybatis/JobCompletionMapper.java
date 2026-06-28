package com.funjson.metaagent.job.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 持久化 Job 层对 Task 和 Job 终态的验收结果。
 */
@Mapper
public interface JobCompletionMapper {

    /**
     * 锁定 Job 完成边界。
     *
     * @param jobId Job ID
     * @return Job UUID 字符串
     */
    @Select("""
            SELECT BIN_TO_UUID(id)
            FROM job
            WHERE id =
                #{jobId,typeHandler=com.funjson.metaagent.common.persistence.mybatis.UuidBinaryTypeHandler}
            FOR UPDATE
            """)
    String lockJob(@Param("jobId") UUID jobId);

    /**
     * 更新 Task 终态并清除活跃运行引用。
     *
     * @param taskId Task ID
     * @param status 状态名称
     * @return 受影响行数
     */
    @Update("""
            UPDATE task
            SET status = #{status},
                active_task_run_id = NULL,
                version = version + 1
            WHERE id = #{taskId,typeHandler=com.funjson.metaagent.common.persistence.mybatis.UuidBinaryTypeHandler}
            """)
    int updateTaskStatus(
            @Param("taskId") UUID taskId,
            @Param("status") String status);

    /**
     * 更新 Job 终态。
     *
     * @param jobId Job ID
     * @param status 状态名称
     * @return 受影响行数
     */
    @Update("""
            UPDATE job
            SET status = #{status},
                version = version + 1
            WHERE id = #{jobId,typeHandler=com.funjson.metaagent.common.persistence.mybatis.UuidBinaryTypeHandler}
            """)
    int updateJobStatus(
            @Param("jobId") UUID jobId,
            @Param("status") String status);

    /**
     * 查询依赖全部完成、可以从 BLOCKED 提升为 READY 的 Task。
     *
     * @param jobId Job ID
     * @return Task UUID 字符串
     */
    @Select("""
            SELECT BIN_TO_UUID(candidate.id)
            FROM task candidate
            WHERE candidate.job_id =
                #{jobId,typeHandler=com.funjson.metaagent.common.persistence.mybatis.UuidBinaryTypeHandler}
              AND candidate.status = 'BLOCKED'
              AND NOT EXISTS (
                  SELECT 1
                  FROM task_dependency dependency
                  JOIN task parent_task
                    ON parent_task.id = dependency.depends_on_task_id
                  WHERE dependency.task_id = candidate.id
                    AND parent_task.status <> 'COMPLETED'
              )
            ORDER BY candidate.sequence_no
            """)
    List<String> findUnblockedTaskIds(
            @Param("jobId") UUID jobId);

    /**
     * 统计尚未完成的 Task。
     *
     * @param jobId Job ID
     * @return 未完成数量
     */
    @Select("""
            SELECT COUNT(*)
            FROM task
            WHERE job_id =
                #{jobId,typeHandler=com.funjson.metaagent.common.persistence.mybatis.UuidBinaryTypeHandler}
              AND status <> 'COMPLETED'
            """)
    long countIncompleteTasks(@Param("jobId") UUID jobId);

    /**
     * 统计当前 READY Task。
     *
     * @param jobId Job ID
     * @return READY 数量
     */
    @Select("""
            SELECT COUNT(*)
            FROM task
            WHERE job_id =
                #{jobId,typeHandler=com.funjson.metaagent.common.persistence.mybatis.UuidBinaryTypeHandler}
              AND status = 'READY'
            """)
    long countReadyTasks(@Param("jobId") UUID jobId);
}
