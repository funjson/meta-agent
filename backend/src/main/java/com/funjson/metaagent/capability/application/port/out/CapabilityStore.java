package com.funjson.metaagent.capability.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.capability.domain.CapabilitySource;
import com.funjson.metaagent.capability.domain.SkillPackage;
import com.funjson.metaagent.capability.domain.SkillResource;

/**
 * 定义 Capability Source、Load 和作用域快照的持久化端口。
 */
public interface CapabilityStore {

    /** @return SkillPackage 版本是否存在 */
    boolean skillPackageExists(CapabilityRef ref);

    /** 插入 SkillPackage 与编译后的 CapabilitySource。 */
    void insertSkillPackage(
            SkillPackage skillPackage,
            String descriptorJson,
            String compiledManifestJson,
            String descriptorChecksum);

    /** 插入 SkillPackage 资源。 */
    void insertSkillResource(
            CapabilityRef packageRef,
            SkillResource resource,
            String executableJson);

    /** @return 激活的不可变 CapabilitySource */
    CapabilitySource requireSource(CapabilityRef ref);

    /** @return 已存在的 CapabilityLoad ID */
    Optional<UUID> findLoad(UUID loopNodeId, CapabilityRef ref);

    /** 插入 CapabilityLoad。 */
    void insertLoad(
            UUID loadId,
            UUID taskRunId,
            UUID loopRunId,
            UUID loopNodeId,
            CapabilitySource source);

    /** 继承父节点的规范型 CapabilityLoad。 */
    void inheritPolicyLoads(
            UUID parentLoopNodeId,
            UUID loopNodeId,
            UUID taskRunId,
            UUID loopRunId);

    /** @return 当前节点生效的 CapabilityLoad */
    List<Map<String, Object>> findAppliedLoads(UUID loopNodeId);

    /** 更新 LoopNode 可恢复作用域快照。 */
    void updateScopedContext(
            UUID loopNodeId,
            String scopedContextJson);
}
