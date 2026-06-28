package com.funjson.metaagent.capability.domain;

import com.funjson.metaagent.runtime.domain.CapabilityRequest;
import com.funjson.metaagent.runtime.domain.ChildJobRequest;
import com.funjson.metaagent.runtime.domain.ContractContribution;

import java.util.List;
import java.util.Map;

/**
 * Skill 导入时生成的不可变编译 Manifest。
 */
public record CompiledSkillManifest(
        CapabilityType type,
        Map<String, Object> policy,
        List<String> steps,
        ChildJobRequest childJob,
        ContractContribution contractContribution,
        CapabilityRequest capabilityRequest,
        String sourceChecksum,
        String manifestChecksum,
        String promptId,
        String promptVersion,
        String promptContentHash,
        List<String> resourcePaths,
        List<SkillExecutable> executables) {

    /**
     * 复制 Manifest 集合。
     */
    public CompiledSkillManifest {
        policy = policy == null ? Map.of() : Map.copyOf(policy);
        steps = steps == null ? List.of() : List.copyOf(steps);
        contractContribution = contractContribution == null
                ? ContractContribution.empty()
                : contractContribution;
        capabilityRequest = capabilityRequest == null
                ? CapabilityRequest.none()
                : capabilityRequest;
        resourcePaths = resourcePaths == null
                ? List.of()
                : List.copyOf(resourcePaths);
        executables = executables == null
                ? List.of()
                : List.copyOf(executables);
        if (type == CapabilityType.CHILD_JOB && childJob == null) {
            throw new IllegalArgumentException(
                    "CHILD_JOB Skill requires a ChildJobRequest");
        }
    }

    /**
     * 返回附带已校验包资源的新 Manifest。
     *
     * @param paths 资源路径
     * @param scriptExecutables 脚本 executable
     * @return 新 Manifest
     */
    public CompiledSkillManifest withPackageResources(
            List<String> paths,
            List<SkillExecutable> scriptExecutables) {
        return new CompiledSkillManifest(
                type,
                policy,
                steps,
                childJob,
                contractContribution,
                capabilityRequest,
                sourceChecksum,
                manifestChecksum,
                promptId,
                promptVersion,
                promptContentHash,
                paths,
                scriptExecutables);
    }
}
