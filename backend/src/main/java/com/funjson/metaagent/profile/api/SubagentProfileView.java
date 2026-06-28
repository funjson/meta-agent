package com.funjson.metaagent.profile.api;

import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.runtime.domain.AuthorityEnvelope;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SubagentProfile API 视图。
 *
 * @param id Profile ID
 * @param agentProfileId AgentProfile ID
 * @param version 版本
 * @param name 名称
 * @param rolePrompt 角色说明
 * @param modelPolicy 模型策略
 * @param skillRefs Skill 引用
 * @param toolAllowlist Tool allowlist
 * @param requestedAuthority 请求权限
 * @param status 状态
 */
public record SubagentProfileView(
        String id,
        String agentProfileId,
        int version,
        String name,
        String rolePrompt,
        Map<String, Object> modelPolicy,
        List<CapabilityRef> skillRefs,
        Set<String> toolAllowlist,
        AuthorityEnvelope requestedAuthority,
        String status) {
}
