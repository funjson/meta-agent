package com.funjson.metaagent.provider.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.funjson.metaagent.provider.api.ModelCatalogView;
import com.funjson.metaagent.provider.api.ModelSpecView;
import com.funjson.metaagent.provider.domain.ModelCapabilities;
import com.funjson.metaagent.provider.domain.ModelSpec;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 读取配置文件中的模型能力目录。
 *
 * <p>上层只选择框架模型 ID，例如 {@code deepseek-chat} 或 {@code glm-4.5}；
 * 该服务负责把它解析为 provider adapter、厂商模型名、上下文窗口和能力快照。</p>
 */
@Service
public class ModelCatalogService {

    private final String defaultModelId;
    private final String fallbackModelId;
    private final List<ModelSpec> models;
    private final Map<String, ModelSpec> byId;

    /**
     * 从 classpath:model-catalog.yml 加载目录。
     */
    public ModelCatalogService() {
        Map<String, Object> root = loadCatalog();
        Map<?, ?> defaults = map(root.get("defaults"));
        this.defaultModelId = text(defaults, "executorModelId", "deepseek-chat");
        this.fallbackModelId = text(defaults, "fallbackModelId", "fake");
        this.models = parseModels(root.get("models"));
        this.byId = models.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ModelSpec::id,
                        Function.identity()));
    }

    /**
     * @return 模型目录 API 视图
     */
    public ModelCatalogView view() {
        return new ModelCatalogView(
                defaultModelId,
                fallbackModelId,
                models.stream().map(this::toView).toList());
    }

    /**
     * @return 默认 executor 模型 ID
     */
    public String defaultModelId() {
        return defaultModelId;
    }

    /**
     * @return fallback 模型 ID
     */
    public String fallbackModelId() {
        return fallbackModelId;
    }

    /**
     * 查询模型。
     *
     * @param modelId 模型 ID
     * @return 模型规格
     */
    public ModelSpec require(String modelId) {
        ModelSpec spec = byId.get(modelId);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown model: " + modelId);
        }
        return spec;
    }

    /**
     * 宽松查询模型。
     *
     * @param modelId 模型 ID
     * @return 可选模型规格
     */
    public Optional<ModelSpec> find(String modelId) {
        return Optional.ofNullable(byId.get(modelId));
    }

    /**
     * 根据 provider 和厂商模型名反查框架模型 ID。
     *
     * @param providerId Provider ID
     * @param providerModel 厂商模型名
     * @return 模型规格
     */
    public Optional<ModelSpec> findByProviderModel(
            String providerId,
            String providerModel) {
        return models.stream()
                .filter(model -> model.providerId().equals(providerId))
                .filter(model -> model.providerModel().equals(providerModel))
                .findFirst();
    }

    /**
     * 根据 legacy provider ID 返回该 provider 的第一个模型。
     *
     * @param providerId Provider ID
     * @return 模型规格
     */
    public Optional<ModelSpec> firstByProvider(String providerId) {
        return models.stream()
                .filter(model -> model.providerId().equals(providerId))
                .findFirst();
    }

    /**
     * 将目录对象转为 API 视图。
     */
    private ModelSpecView toView(ModelSpec spec) {
        return new ModelSpecView(
                spec.id(),
                spec.displayName(),
                spec.providerId(),
                spec.providerModel(),
                spec.family(),
                spec.contextWindow(),
                spec.modalities(),
                spec.capabilities());
    }

    /**
     * 加载 YAML 文件。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadCatalog() {
        YamlMapFactoryBean factory = new YamlMapFactoryBean();
        factory.setResources(new ClassPathResource("model-catalog.yml"));
        Map<String, Object> value = factory.getObject();
        return value == null ? Map.of() : value;
    }

    /**
     * 解析模型数组。
     */
    private List<ModelSpec> parseModels(Object raw) {
        if (!(raw instanceof List<?> items)) {
            return List.of();
        }
        List<ModelSpec> parsed = new ArrayList<>();
        for (Object item : items) {
            Map<?, ?> values = map(item);
            Map<?, ?> caps = map(values.get("capabilities"));
            parsed.add(new ModelSpec(
                    text(values, "id", ""),
                    text(values, "displayName", ""),
                    text(values, "providerId", ""),
                    text(values, "providerModel", ""),
                    text(values, "family", ""),
                    number(values, "contextWindow", 0),
                    strings(values.get("modalities")),
                    new ModelCapabilities(
                            bool(caps, "toolCalling"),
                            bool(caps, "reasoning"),
                            bool(caps, "reasoningContent"),
                            bool(caps, "thinkingMode"),
                            bool(caps, "vision"))));
        }
        return List.copyOf(parsed);
    }

    /**
     * 安全转换为 Map。
     */
    private Map<?, ?> map(Object raw) {
        return raw instanceof Map<?, ?> value ? value : new LinkedHashMap<>();
    }

    /**
     * 读取字符串字段。
     */
    private String text(Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null || String.valueOf(value).isBlank()
                ? fallback
                : String.valueOf(value).trim();
    }

    /**
     * 读取整数字段。
     */
    private int number(Map<?, ?> values, String key, int fallback) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    /**
     * 读取布尔字段。
     */
    private boolean bool(Map<?, ?> values, String key) {
        Object value = values.get(key);
        return value instanceof Boolean bool
                ? bool
                : Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * 转换字符串集合。
     */
    private List<String> strings(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(String::valueOf)
                .toList();
    }
}
