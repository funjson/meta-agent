package com.funjson.metaagent.profile.infrastructure.persistence.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * SubagentProfile MyBatis Mapper。
 */
@Mapper
public interface SubagentProfilePersistenceMapper {

    /** @return 指定版本行 */
    Map<String, Object> find(
            @Param("id") String id,
            @Param("version") int version);

    /** @return AgentProfile 下的版本行 */
    List<Map<String, Object>> findAll(
            @Param("agentProfileId") String agentProfileId);

    /** @return 插入行数 */
    int insert(
            @Param("id") String id,
            @Param("agentProfileId") String agentProfileId,
            @Param("version") int version,
            @Param("name") String name,
            @Param("rolePrompt") String rolePrompt,
            @Param("modelPolicyJson") String modelPolicyJson,
            @Param("skillRefsJson") String skillRefsJson,
            @Param("toolAllowlistJson") String toolAllowlistJson,
            @Param("authorityJson") String authorityJson,
            @Param("status") String status);
}
