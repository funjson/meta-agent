package com.funjson.metaagent.capability.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.domain.CapabilityType;
import com.funjson.metaagent.capability.domain.CompiledSkillManifest;
import com.funjson.metaagent.prompt.application.PromptRegistry;
import com.funjson.metaagent.prompt.domain.PromptUseCase;
import com.funjson.metaagent.provider.application.ModelProviderRegistry;
import com.funjson.metaagent.provider.application.port.out.ProviderSecretPort;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.runtime.domain.CapabilityRequest;
import com.funjson.metaagent.runtime.domain.ChildJobRequest;
import com.funjson.metaagent.runtime.domain.ContractContribution;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 在 Skill 导入阶段调用模型，把非结构化内容编译为不可变 Manifest。
 */
@Service
public class SkillCompiler {

    private final ModelProviderRegistry providers;
    private final ProviderSecretPort secretStore;
    private final PromptRegistry prompts;
    private final ObjectMapper objectMapper;

    /**
     * 创建 Skill Compiler。
     */
    public SkillCompiler(
            ModelProviderRegistry providers,
            ProviderSecretPort secretStore,
            PromptRegistry prompts,
            ObjectMapper objectMapper) {
        this.providers = providers;
        this.secretStore = secretStore;
        this.prompts = prompts;
        this.objectMapper = objectMapper;
    }

    /**
     * 编译并校验 Skill 原文。
     *
     * @param sourceId Skill ID
     * @param sourceVersion Skill 版本
     * @param rawContent 非结构化原文
     * @return 不可变 Manifest
     */
    public CompiledSkillManifest compile(
            String sourceId,
            int sourceVersion,
            String rawContent) {
        if (!secretStore.configured()) {
            throw new RuntimeStateException(
                    "SKILL_COMPILER_MODEL_UNAVAILABLE",
                    "Skill compilation requires a configured model");
        }
        var prompt = prompts.render(
                PromptUseCase.SKILL_COMPILATION,
                Map.of(
                        "sourceId", sourceId,
                        "sourceVersion", String.valueOf(sourceVersion),
                        "rawContent", rawContent));
        var response = providers.require("deepseek").generate(
                new ModelRequest(
                        null,
                        null,
                        "Compile Skill " + sourceId,
                        prompt,
                        1600));
        try {
            JsonNode root = objectMapper.readTree(
                    stripCodeFence(response.content()));
            CapabilityType type = CapabilityType.valueOf(
                    root.path("type").asText());
            Map<String, Object> policy = objectMapper.convertValue(
                    root.path("policy"),
                    Map.class);
            List<String> steps = root.path("steps").isArray()
                    ? objectMapper.convertValue(
                            root.path("steps"),
                            objectMapper.getTypeFactory()
                                    .constructCollectionType(
                                            List.class,
                                            String.class))
                    : List.of();
            ChildJobRequest childJob =
                    root.path("childJob").isMissingNode()
                            || root.path("childJob").isNull()
                            ? null
                            : objectMapper.convertValue(
                                    root.path("childJob"),
                                    ChildJobRequest.class);
            ContractContribution contract = root.path(
                            "contractContribution").isMissingNode()
                    ? ContractContribution.empty()
                    : objectMapper.convertValue(
                            root.path("contractContribution"),
                            ContractContribution.class);
            CapabilityRequest capabilityRequest = root.path(
                            "capabilityRequest").isMissingNode()
                    ? CapabilityRequest.none()
                    : objectMapper.convertValue(
                            root.path("capabilityRequest"),
                            CapabilityRequest.class);
            String manifestJson = objectMapper.writeValueAsString(root);
            return new CompiledSkillManifest(
                    type,
                    policy,
                    steps,
                    childJob,
                    contract,
                    capabilityRequest,
                    sha256(rawContent),
                    sha256(manifestJson),
                    prompt.promptId(),
                    prompt.version(),
                    prompt.contentHash(),
                    List.of(),
                    List.of());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new RuntimeStateException(
                    "INVALID_SKILL_MANIFEST",
                    "Skill compiler returned an invalid Manifest");
        }
    }

    /** 移除模型可能返回的 JSON Markdown 围栏。 */
    private String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    /** 计算 SHA-256。 */
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(
                                    StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception);
        }
    }
}
