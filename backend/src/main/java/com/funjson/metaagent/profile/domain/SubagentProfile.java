package com.funjson.metaagent.profile.domain;

import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.runtime.domain.AuthorityEnvelope;
import com.funjson.metaagent.runtime.domain.SubagentProfileRef;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Child Job 可选使用的不可变 SubagentProfile。
 *
 * @param ref 版本引用
 * @param agentProfileId 所属 AgentProfile
 * @param name 名称
 * @param rolePrompt 角色说明
 * @param modelPolicy 模型偏好
 * @param skillRefs Skill 引用
 * @param toolAllowlist Tool allowlist
 * @param requestedAuthority 请求权限
 * @param status 状态
 */
public record SubagentProfile(
        SubagentProfileRef ref,
        String agentProfileId,
        String name,
        String rolePrompt,
        Map<String, Object> modelPolicy,
        List<CapabilityRef> skillRefs,
        Set<String> toolAllowlist,
        AuthorityEnvelope requestedAuthority,
        String status) {

    /**
     * 复制集合并校验身份。
     */
    public SubagentProfile {
        if (ref == null
                || agentProfileId == null
                || agentProfileId.isBlank()
                || name == null
                || name.isBlank()
                || rolePrompt == null
                || rolePrompt.isBlank()) {
            throw new IllegalArgumentException(
                    "SubagentProfile metadata is required");
        }
        modelPolicy = modelPolicy == null
                ? Map.of()
                : Map.copyOf(modelPolicy);
        skillRefs = skillRefs == null
                ? List.of()
                : List.copyOf(skillRefs);
        toolAllowlist = toolAllowlist == null
                ? Set.of()
                : Set.copyOf(toolAllowlist);
        requestedAuthority = requestedAuthority == null
                ? new AuthorityEnvelope(
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of(),
                        Set.of())
                : requestedAuthority;
        status = status == null ? "ACTIVE" : status;
    }

    /**
     * 计算只能收窄的有效权限。
     *
     * @param parentAuthority 父 Job 权限
     * @return 有效权限
     */
    public AuthorityEnvelope effectiveAuthority(
            AuthorityEnvelope parentAuthority) {
        return parentAuthority.narrow(requestedAuthority);
    }
}
