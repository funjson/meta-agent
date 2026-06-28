package com.funjson.metaagent.profile.api;

import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.runtime.domain.AuthorityEnvelope;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 创建 SubagentProfile 请求。
 *
 * @param id Profile ID
 * @param agentProfileId 所属 AgentProfile
 * @param version 版本
 * @param name 名称
 * @param rolePrompt 角色说明
 * @param modelPolicy 模型策略
 * @param skillRefs Skill 引用
 * @param toolAllowlist Tool allowlist
 * @param requestedAuthority 请求权限
 */
public record CreateSubagentProfileRequest(
        @NotBlank @Size(max = 100) String id,
        @NotBlank @Size(max = 80) String agentProfileId,
        @Min(1) int version,
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Size(max = 20_000) String rolePrompt,
        Map<String, Object> modelPolicy,
        List<CapabilityRef> skillRefs,
        Set<String> toolAllowlist,
        AuthorityEnvelope requestedAuthority) {
}
