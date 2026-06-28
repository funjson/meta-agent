package com.funjson.metaagent.capability;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.funjson.metaagent.capability.application.CapabilityRegistry;
import com.funjson.metaagent.capability.domain.CapabilityAdapter;
import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.capability.domain.CapabilityScopeType;
import com.funjson.metaagent.capability.domain.CapabilitySource;
import com.funjson.metaagent.capability.domain.CapabilityType;
import com.funjson.metaagent.capability.infrastructure.persistence.mybatis.CapabilityRepository;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.junit.jupiter.api.Test;

/**
 * 验证 CapabilitySource 在解析前执行内容完整性校验。
 */
class CapabilityRegistryTest {

    @Test
    void rejectsDescriptorWithInvalidChecksum() {
        CapabilityRepository repository = mock(CapabilityRepository.class);
        CapabilityAdapter adapter = mock(CapabilityAdapter.class);
        when(adapter.id()).thenReturn("adapter");
        CapabilityRef ref = new CapabilityRef("capability", 1);
        when(repository.requireSource(ref)).thenReturn(
                new CapabilitySource(
                        ref,
                        "Capability",
                        "CONFIG",
                        "adapter",
                        CapabilityType.POLICY,
                        CapabilityScopeType.LOOP_NODE_SUBTREE,
                        "{}",
                        "invalid"));
        CapabilityRegistry registry = new CapabilityRegistry(
                repository,
                List.of(adapter));

        assertThatThrownBy(() -> registry.resolve(ref))
                .isInstanceOf(RuntimeStateException.class)
                .extracting("code")
                .isEqualTo("CAPABILITY_CHECKSUM_MISMATCH");
    }
}
