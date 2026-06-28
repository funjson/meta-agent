package com.funjson.metaagent.capability.infrastructure.persistence.mybatis;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.capability.domain.CapabilityScopeType;
import com.funjson.metaagent.capability.domain.CapabilitySource;
import com.funjson.metaagent.capability.domain.CapabilityType;
import com.funjson.metaagent.capability.domain.SkillPackage;
import com.funjson.metaagent.capability.domain.SkillResource;
import com.funjson.metaagent.capability.application.port.out.CapabilityStore;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.stereotype.Repository;

/**
 * 适配 Capability Application 与 MyBatis 持久化。
 */
@Repository
public class CapabilityRepository implements CapabilityStore {

    private final CapabilityPersistenceMapper mapper;

    /**
     * 创建 Capability Repository。
     *
     * @param mapper Capability Mapper
     */
    public CapabilityRepository(CapabilityPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    public boolean skillPackageExists(CapabilityRef ref) {
        return mapper.countSkillPackage(ref.id(), ref.version()) > 0;
    }

    /** {@inheritDoc} */
    public void insertSkillPackage(
            SkillPackage skillPackage,
            String descriptorJson,
            String compiledManifestJson,
            String descriptorChecksum) {
        var manifest = skillPackage.compiledManifest();
        mapper.insertSkillPackage(
                skillPackage.ref().id(),
                skillPackage.ref().version(),
                skillPackage.name(),
                skillPackage.packageChecksum());
        mapper.insertCompiledCapabilitySource(
                skillPackage.ref().id(),
                skillPackage.ref().version(),
                skillPackage.name(),
                manifest.type().name(),
                manifest.type() == CapabilityType.POLICY
                        ? CapabilityScopeType.LOOP_NODE_SUBTREE.name()
                        : CapabilityScopeType.LOOP_RUN.name(),
                descriptorJson,
                descriptorChecksum,
                skillPackage.skillMarkdown(),
                compiledManifestJson,
                manifest.promptId(),
                Integer.parseInt(
                        manifest.promptVersion().replaceFirst("^v", "")),
                manifest.sourceChecksum());
    }

    /** {@inheritDoc} */
    public void insertSkillResource(
            CapabilityRef packageRef,
            SkillResource resource,
            String executableJson) {
        mapper.insertSkillResource(
                packageRef.id(),
                packageRef.version(),
                resource.path(),
                resource.type().name(),
                resource.contentHash(),
                resource.content(),
                executableJson);
    }

    /**
     * 查询激活的不可变 CapabilitySource。
     *
     * @param ref 来源引用
     * @return 来源
     */
    public CapabilitySource requireSource(CapabilityRef ref) {
        Map<String, Object> row = mapper.findSource(
                ref.id(),
                ref.version());
        if (row == null) {
            throw new RuntimeStateException(
                    "CAPABILITY_SOURCE_NOT_FOUND",
                    "Active capability source not found: "
                            + ref.id()
                            + "@"
                            + ref.version());
        }
        return new CapabilitySource(
                ref,
                text(row, "name"),
                text(row, "sourceType"),
                text(row, "adapterId"),
                CapabilityType.valueOf(text(row, "capabilityType")),
                CapabilityScopeType.valueOf(text(row, "scopeType")),
                text(row, "descriptorJson"),
                text(row, "checksum"));
    }

    /**
     * 查询已应用的 CapabilityLoad。
     *
     * @param loopNodeId LoopNode ID
     * @param ref 来源引用
     * @return Load ID
     */
    public Optional<UUID> findLoad(
            UUID loopNodeId,
            CapabilityRef ref) {
        Map<String, Object> row = mapper.findLoad(
                loopNodeId,
                ref.id(),
                ref.version());
        return row == null
                ? Optional.empty()
                : Optional.of(UUID.fromString(text(row, "loadId")));
    }

    /**
     * 插入 CapabilityLoad。
     *
     * @param loadId Load ID
     * @param taskRunId TaskRun ID
     * @param loopRunId LoopRun ID
     * @param loopNodeId LoopNode ID
     * @param source 来源
     */
    public void insertLoad(
            UUID loadId,
            UUID taskRunId,
            UUID loopRunId,
            UUID loopNodeId,
            CapabilitySource source) {
        mapper.insertLoad(
                loadId,
                taskRunId,
                loopRunId,
                loopNodeId,
                source.ref().id(),
                source.ref().version(),
                source.adapterId(),
                source.capabilityType().name(),
                source.scopeType().name(),
                source.scopeType() == CapabilityScopeType.LOOP_RUN
                        ? loopRunId
                        : loopNodeId,
                source.descriptorJson());
    }

    /**
     * 继承父节点的规范型 Capability。
     *
     * @param parentLoopNodeId 父节点
     * @param loopNodeId 当前节点
     * @param taskRunId TaskRun ID
     * @param loopRunId LoopRun ID
     */
    public void inheritPolicyLoads(
            UUID parentLoopNodeId,
            UUID loopNodeId,
            UUID taskRunId,
            UUID loopRunId) {
        mapper.inheritPolicyLoads(
                parentLoopNodeId,
                loopNodeId,
                taskRunId,
                loopRunId);
    }

    /**
     * 查询当前节点生效的 Capability。
     *
     * @param loopNodeId LoopNode ID
     * @return Load 行
     */
    public List<Map<String, Object>> findAppliedLoads(UUID loopNodeId) {
        return mapper.findAppliedLoads(loopNodeId);
    }

    /**
     * 更新 LoopNode 的可恢复局部上下文。
     *
     * @param loopNodeId LoopNode ID
     * @param scopedContextJson 作用域 JSON
     */
    public void updateScopedContext(
            UUID loopNodeId,
            String scopedContextJson) {
        if (mapper.updateLoopNodeScopedContext(
                loopNodeId,
                scopedContextJson) != 1) {
            throw new RuntimeStateException(
                    "LOOP_NODE_NOT_FOUND",
                    "Loop node not found: " + loopNodeId);
        }
    }

    /** 读取字符串。 */
    private String text(Map<String, Object> row, String key) {
        return String.valueOf(row.get(key));
    }
}
