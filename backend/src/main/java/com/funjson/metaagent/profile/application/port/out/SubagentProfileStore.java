package com.funjson.metaagent.profile.application.port.out;

import com.funjson.metaagent.profile.domain.SubagentProfile;
import com.funjson.metaagent.runtime.domain.SubagentProfileRef;

import java.util.List;
import java.util.Optional;

/**
 * SubagentProfile 持久化端口。
 */
public interface SubagentProfileStore {

    /** @return 指定版本 */
    Optional<SubagentProfile> find(SubagentProfileRef ref);

    /** @return AgentProfile 下的版本列表 */
    List<SubagentProfile> findAll(String agentProfileId);

    /** 插入不可变版本。 */
    void insert(SubagentProfile profile);
}
