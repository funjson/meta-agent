package com.funjson.metaagent.profile;

import com.funjson.metaagent.profile.domain.SubagentProfile;
import com.funjson.metaagent.runtime.domain.AuthorityEnvelope;
import com.funjson.metaagent.runtime.domain.SubagentProfileRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 SubagentProfile 权限只能在父级包络内收窄。
 */
class SubagentProfileTest {

    @Test
    void intersectsRequestedAuthorityWithParent() {
        AuthorityEnvelope parent = new AuthorityEnvelope(
                Set.of("deepseek"),
                Set.of("read", "write"),
                Set.of("workspace"),
                Set.of("project"),
                Set.of("network"));
        SubagentProfile profile = new SubagentProfile(
                new SubagentProfileRef("researcher", 1),
                "general-agent",
                "Researcher",
                "Collect evidence",
                Map.of(),
                List.of(),
                Set.of("read", "network"),
                new AuthorityEnvelope(
                        Set.of("deepseek", "other"),
                        Set.of("read", "network"),
                        Set.of("workspace"),
                        Set.of("project", "outside"),
                        Set.of("network")),
                "ACTIVE");

        AuthorityEnvelope effective = profile.effectiveAuthority(parent);

        assertThat(effective.allowedProviders())
                .containsExactly("deepseek");
        assertThat(effective.allowedTools())
                .containsExactly("read");
        assertThat(effective.fileScopes())
                .containsExactly("project");
    }
}
