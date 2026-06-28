package com.funjson.metaagent.profile.application;

import com.funjson.metaagent.profile.api.CreateSubagentProfileRequest;
import com.funjson.metaagent.profile.api.SubagentProfileView;
import com.funjson.metaagent.profile.application.port.out.SubagentProfileStore;
import com.funjson.metaagent.profile.domain.SubagentProfile;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.runtime.domain.SubagentProfileRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理不可变 SubagentProfile 版本。
 */
@Service
public class SubagentProfileService {

    private final SubagentProfileStore store;

    /**
     * 创建 SubagentProfile Service。
     *
     * @param store 持久化端口
     */
    public SubagentProfileService(SubagentProfileStore store) {
        this.store = store;
    }

    /**
     * 创建并激活一个不可变版本。
     *
     * @param request 创建请求
     * @return Profile 视图
     */
    @Transactional
    public SubagentProfileView create(
            CreateSubagentProfileRequest request) {
        SubagentProfileRef ref = new SubagentProfileRef(
                request.id().trim(),
                request.version());
        if (store.find(ref).isPresent()) {
            throw new RuntimeStateException(
                    "SUBAGENT_PROFILE_VERSION_EXISTS",
                    "SubagentProfile version already exists: "
                            + ref.id()
                            + "@"
                            + ref.version());
        }
        SubagentProfile profile = new SubagentProfile(
                ref,
                request.agentProfileId().trim(),
                request.name().trim(),
                request.rolePrompt(),
                request.modelPolicy(),
                request.skillRefs(),
                request.toolAllowlist(),
                request.requestedAuthority(),
                "ACTIVE");
        store.insert(profile);
        return view(profile);
    }

    /**
     * 校验 Child Job 引用属于同一 AgentProfile。
     *
     * @param agentProfileId 父 AgentProfile
     * @param ref SubagentProfile 引用
     * @return Profile
     */
    @Transactional(readOnly = true)
    public SubagentProfile requireCompatible(
            String agentProfileId,
            SubagentProfileRef ref) {
        SubagentProfile profile = store.find(ref)
                .filter(candidate -> "ACTIVE".equals(candidate.status()))
                .orElseThrow(() -> new RuntimeStateException(
                        "SUBAGENT_PROFILE_NOT_FOUND",
                        "Active SubagentProfile is unavailable"));
        if (!profile.agentProfileId().equals(agentProfileId)) {
            throw new RuntimeStateException(
                    "SUBAGENT_PROFILE_AGENT_MISMATCH",
                    "SubagentProfile must belong to the parent AgentProfile");
        }
        return profile;
    }

    /**
     * 查询 AgentProfile 下所有版本。
     *
     * @param agentProfileId AgentProfile ID
     * @return Profile 视图
     */
    @Transactional(readOnly = true)
    public List<SubagentProfileView> list(String agentProfileId) {
        return store.findAll(agentProfileId).stream()
                .map(this::view)
                .toList();
    }

    /** 转换 API 视图。 */
    private SubagentProfileView view(SubagentProfile profile) {
        return new SubagentProfileView(
                profile.ref().id(),
                profile.agentProfileId(),
                profile.ref().version(),
                profile.name(),
                profile.rolePrompt(),
                profile.modelPolicy(),
                profile.skillRefs(),
                profile.toolAllowlist(),
                profile.requestedAuthority(),
                profile.status());
    }
}
