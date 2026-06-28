package com.funjson.metaagent.capability.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.funjson.metaagent.capability.domain.CapabilityAdapter;
import com.funjson.metaagent.capability.domain.CapabilityDescriptor;
import com.funjson.metaagent.capability.domain.CapabilityRef;
import com.funjson.metaagent.capability.application.port.out.CapabilityStore;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.stereotype.Service;

/**
 * 按不可变来源版本解析 Capability。
 */
@Service
public class CapabilityRegistry {

    private final CapabilityStore repository;
    private final Map<String, CapabilityAdapter> adapters;

    /**
     * 创建 Capability Registry。
     *
     * @param repository Capability Repository
     * @param adapters 已注册 Adapter
     */
    public CapabilityRegistry(
            CapabilityStore repository,
            java.util.List<CapabilityAdapter> adapters) {
        this.repository = repository;
        this.adapters = adapters.stream()
                .collect(Collectors.toUnmodifiableMap(
                        CapabilityAdapter::id,
                        Function.identity()));
    }

    /**
     * 解析指定 Capability。
     *
     * @param ref 来源引用
     * @return 统一描述
     */
    public CapabilityDescriptor resolve(CapabilityRef ref) {
        var source = repository.requireSource(ref);
        if (!sha256(source.descriptorJson()).equals(source.checksum())) {
            throw new RuntimeStateException(
                    "CAPABILITY_CHECKSUM_MISMATCH",
                    "Capability source checksum is invalid: "
                            + ref.id()
                            + "@"
                            + ref.version());
        }
        CapabilityAdapter adapter = adapters.get(source.adapterId());
        if (adapter == null) {
            throw new RuntimeStateException(
                    "CAPABILITY_ADAPTER_NOT_FOUND",
                    "Capability adapter is unavailable: "
                            + source.adapterId());
        }
        return adapter.parse(source);
    }

    /**
     * 计算持久化描述的 SHA-256。
     *
     * @param value 描述 JSON
     * @return 十六进制校验和
     */
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception);
        }
    }
}
