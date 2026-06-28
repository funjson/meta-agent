package com.funjson.metaagent.capability.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 定义 CapabilitySource、CapabilityLoad 与局部作用域的 MyBatis 映射。
 */
@Mapper
public interface CapabilityPersistenceMapper {

    /** @return SkillPackage 版本数量 */
    int countSkillPackage(
            @Param("packageId") String packageId,
            @Param("packageVersion") int packageVersion);

    /** @return 插入行数 */
    int insertSkillPackage(
            @Param("packageId") String packageId,
            @Param("packageVersion") int packageVersion,
            @Param("name") String name,
            @Param("manifestChecksum") String manifestChecksum);

    /** @return 插入行数 */
    int insertCompiledCapabilitySource(
            @Param("sourceId") String sourceId,
            @Param("sourceVersion") int sourceVersion,
            @Param("name") String name,
            @Param("capabilityType") String capabilityType,
            @Param("scopeType") String scopeType,
            @Param("descriptorJson") String descriptorJson,
            @Param("descriptorChecksum") String descriptorChecksum,
            @Param("rawContent") String rawContent,
            @Param("compiledManifestJson") String compiledManifestJson,
            @Param("promptId") String promptId,
            @Param("promptVersion") int promptVersion,
            @Param("contentHash") String contentHash);

    /** @return 插入行数 */
    int insertSkillResource(
            @Param("packageId") String packageId,
            @Param("packageVersion") int packageVersion,
            @Param("resourcePath") String resourcePath,
            @Param("resourceType") String resourceType,
            @Param("contentHash") String contentHash,
            @Param("contentText") String contentText,
            @Param("executableJson") String executableJson);

    /** @return CapabilitySource 行 */
    Map<String, Object> findSource(
            @Param("sourceId") String sourceId,
            @Param("sourceVersion") int sourceVersion);

    /** @return CapabilityLoad 行 */
    Map<String, Object> findLoad(
            @Param("loopNodeId") UUID loopNodeId,
            @Param("sourceId") String sourceId,
            @Param("sourceVersion") int sourceVersion);

    /** @return 插入行数 */
    int insertLoad(
            @Param("loadId") UUID loadId,
            @Param("taskRunId") UUID taskRunId,
            @Param("loopRunId") UUID loopRunId,
            @Param("loopNodeId") UUID loopNodeId,
            @Param("sourceId") String sourceId,
            @Param("sourceVersion") int sourceVersion,
            @Param("adapterId") String adapterId,
            @Param("capabilityType") String capabilityType,
            @Param("scopeRootType") String scopeRootType,
            @Param("scopeRootId") UUID scopeRootId,
            @Param("descriptorJson") String descriptorJson);

    /** @return 继承的规范型 Capability 数量 */
    int inheritPolicyLoads(
            @Param("parentLoopNodeId") UUID parentLoopNodeId,
            @Param("loopNodeId") UUID loopNodeId,
            @Param("taskRunId") UUID taskRunId,
            @Param("loopRunId") UUID loopRunId);

    /** @return 当前节点生效的 CapabilityLoad */
    List<Map<String, Object>> findAppliedLoads(
            @Param("loopNodeId") UUID loopNodeId);

    /** @return 更新行数 */
    int updateLoopNodeScopedContext(
            @Param("loopNodeId") UUID loopNodeId,
            @Param("scopedContextJson") String scopedContextJson);
}
