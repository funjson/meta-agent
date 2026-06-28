package com.funjson.metaagent.job.infrastructure.persistence.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TaskGraphTemplate MyBatis Mapper。
 */
@Mapper
public interface TaskGraphTemplatePersistenceMapper {

    /** @return 已有模板 ID */
    String findTemplateId(
            @Param("agentProfileId") String agentProfileId,
            @Param("templateKey") String templateKey);

    /** @return 下一个版本 */
    int nextVersion(
            @Param("agentProfileId") String agentProfileId,
            @Param("templateKey") String templateKey);

    /** @return 更新行数 */
    int retireActiveVersions(
            @Param("agentProfileId") String agentProfileId,
            @Param("templateKey") String templateKey);

    /** @return 插入行数 */
    int insert(
            @Param("id") UUID id,
            @Param("agentProfileId") String agentProfileId,
            @Param("templateKey") String templateKey,
            @Param("version") int version,
            @Param("name") String name,
            @Param("intentLabelsJson") String intentLabelsJson,
            @Param("graphJson") String graphJson,
            @Param("checksum") String checksum);

    /** @return 指定版本行 */
    Map<String, Object> find(
            @Param("id") UUID id,
            @Param("version") int version);

    /** @return Profile 全部版本行 */
    List<Map<String, Object>> findAll(
            @Param("agentProfileId") String agentProfileId);

    /** @return Profile 激活版本行 */
    List<Map<String, Object>> findActive(
            @Param("agentProfileId") String agentProfileId);
}
