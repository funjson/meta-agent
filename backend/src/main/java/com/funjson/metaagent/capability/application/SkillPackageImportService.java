package com.funjson.metaagent.capability.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.api.ImportSkillPackageRequest;
import com.funjson.metaagent.capability.api.SkillPackageView;
import com.funjson.metaagent.capability.application.port.out.CapabilityStore;
import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.capability.domain.CapabilityType;
import com.funjson.metaagent.capability.domain.CompiledSkillManifest;
import com.funjson.metaagent.capability.domain.SkillPackage;
import com.funjson.metaagent.capability.domain.SkillResource;
import com.funjson.metaagent.capability.domain.SkillExecutable;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 校验、编译并持久化版本化 SkillPackage。
 */
@Service
public class SkillPackageImportService {

    private final SkillCompiler compiler;
    private final CapabilityStore store;
    private final ObjectMapper objectMapper;

    /**
     * 创建 SkillPackage 导入服务。
     *
     * @param compiler SkillCompiler
     * @param store Capability Store
     * @param objectMapper JSON Mapper
     */
    public SkillPackageImportService(
            SkillCompiler compiler,
            CapabilityStore store,
            ObjectMapper objectMapper) {
        this.compiler = compiler;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    /**
     * 导入不可变 SkillPackage。
     *
     * @param request 导入请求
     * @return SkillPackage 视图
     */
    @Transactional
    public SkillPackageView importPackage(
            ImportSkillPackageRequest request) {
        CapabilityRef ref = new CapabilityRef(
                request.id().trim(),
                request.version());
        if (store.skillPackageExists(ref)) {
            throw new RuntimeStateException(
                    "SKILL_PACKAGE_VERSION_EXISTS",
                    "SkillPackage version already exists: "
                            + ref.id()
                            + "@"
                            + ref.version());
        }
        List<SkillResource> resources = resources(request);
        List<SkillExecutable> executables = resources.stream()
                .map(SkillResource::executable)
                .filter(java.util.Objects::nonNull)
                .toList();
        CompiledSkillManifest manifest = compiler.compile(
                        ref.id(),
                        ref.version(),
                        request.skillMarkdown())
                .withPackageResources(
                        resources.stream()
                                .map(SkillResource::path)
                                .toList(),
                        executables);
        SkillPackage skillPackage = new SkillPackage(
                ref,
                request.name().trim(),
                request.skillMarkdown(),
                resources,
                manifest,
                packageChecksum(request.skillMarkdown(), resources));
        String descriptorJson = descriptorJson(manifest);
        store.insertSkillPackage(
                skillPackage,
                descriptorJson,
                json(manifest),
                sha256(descriptorJson));
        for (SkillResource resource : resources) {
            store.insertSkillResource(
                    ref,
                    resource,
                    resource.executable() == null
                            ? null
                            : json(resource.executable()));
        }
        return new SkillPackageView(
                ref.id(),
                ref.version(),
                skillPackage.name(),
                manifest.type(),
                skillPackage.packageChecksum(),
                manifest.resourcePaths(),
                manifest.executables().stream()
                        .map(SkillExecutable::toolId)
                        .toList());
    }

    /** 转换并校验资源。 */
    private List<SkillResource> resources(
            ImportSkillPackageRequest request) {
        if (request.resources() == null) {
            return List.of();
        }
        return request.resources().stream()
                .map(resource -> new SkillResource(
                        resource.path(),
                        resource.type(),
                        resource.content(),
                        sha256(resource.content() == null
                                ? ""
                                : resource.content()),
                        resource.executable()))
                .toList();
    }

    /** 生成运行时 JsonCapabilityAdapter 描述。 */
    private String descriptorJson(CompiledSkillManifest manifest) {
        return switch (manifest.type()) {
            case POLICY -> json(Map.of(
                    "instructions", manifest.steps(),
                    "policy", manifest.policy(),
                    "resources", manifest.resourcePaths(),
                    "executables", manifest.executables()));
            case STEP -> json(Map.of(
                    "childGoal", manifest.steps().isEmpty()
                            ? "执行 Skill 步骤"
                            : manifest.steps().getFirst(),
                    "steps", manifest.steps(),
                    "resources", manifest.resourcePaths(),
                    "executables", manifest.executables()));
            case CHILD_JOB -> json(Map.of(
                    "childJob", manifest.childJob(),
                    "resources", manifest.resourcePaths(),
                    "executables", manifest.executables()));
        };
    }

    /** 计算稳定整包 checksum。 */
    private String packageChecksum(
            String skillMarkdown,
            List<SkillResource> resources) {
        StringBuilder canonical = new StringBuilder(skillMarkdown);
        resources.stream()
                .sorted(Comparator.comparing(SkillResource::path))
                .forEach(resource -> canonical
                        .append('\n')
                        .append(resource.path())
                        .append(':')
                        .append(resource.contentHash()));
        return sha256(canonical.toString());
    }

    /** 序列化 JSON。 */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to serialize SkillPackage",
                    exception);
        }
    }

    /** 计算 SHA-256。 */
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception);
        }
    }
}
