package com.funjson.metaagent.capability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.capability.domain.CapabilityScopeType;
import com.funjson.metaagent.capability.domain.CapabilitySource;
import com.funjson.metaagent.capability.domain.CapabilityType;
import com.funjson.metaagent.capability.infrastructure.adapter.JsonCapabilityAdapter;
import org.junit.jupiter.api.Test;

/**
 * 验证 JSON Skill 描述可以转换为统一 CapabilityDescriptor。
 */
class JsonCapabilityAdapterTest {

    @Test
    void parsesVersionedPolicyCapability() {
        var adapter = new JsonCapabilityAdapter(new ObjectMapper());
        var source = new CapabilitySource(
                new CapabilityRef("policy", 1),
                "Policy",
                "CONFIG",
                JsonCapabilityAdapter.ADAPTER_ID,
                CapabilityType.POLICY,
                CapabilityScopeType.LOOP_NODE_SUBTREE,
                """
                {
                  "instructions": ["keep scope local"],
                  "policy": {"requireEvidence": true}
                }
                """,
                "checksum");

        var descriptor = adapter.parse(source);

        assertThat(descriptor.type()).isEqualTo(CapabilityType.POLICY);
        assertThat(descriptor.parameters())
                .containsKey("instructions")
                .containsKey("policy");
    }
}
