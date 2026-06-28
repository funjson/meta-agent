package com.funjson.metaagent.prompt.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.funjson.metaagent.prompt.application.PromptRegistry;
import com.funjson.metaagent.prompt.domain.PromptUseCase;
import com.funjson.metaagent.prompt.domain.RenderedPrompt;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 从 classpath 加载并缓存 Prompt 模板。
 *
 * <p>当前实现适合单机模块化单体。未来接入数据库或 Prompt 平台时，只需新增
 * {@link PromptRegistry} Adapter，不需要修改 Control、Loop 或 Provider。</p>
 */
@Component
public class ClasspathPromptRegistry implements PromptRegistry {

    private final ResourceLoader resourceLoader;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * 创建 classpath Prompt Registry。
     *
     * @param resourceLoader Spring 资源加载器
     */
    public ClasspathPromptRegistry(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 加载模板、验证变量并生成内容哈希。
     *
     * @param useCase Prompt 用途
     * @param variables 模板变量
     * @return 渲染后的 Prompt
     */
    @Override
    public RenderedPrompt render(
            PromptUseCase useCase,
            Map<String, String> variables) {
        validateVariables(useCase.requiredVariables(), variables);
        String systemMessage = substitute(load(useCase.systemResource()), variables);
        String userMessage = substitute(load(useCase.userResource()), variables);
        return new RenderedPrompt(
                useCase.id(),
                useCase.version(),
                systemMessage,
                userMessage,
                sha256(systemMessage + "\n---\n" + userMessage));
    }

    /**
     * 校验所有必填变量都已提供。
     *
     * @param requiredVariables 必填变量
     * @param variables 实际变量
     */
    private void validateVariables(
            Set<String> requiredVariables,
            Map<String, String> variables) {
        for (String variable : requiredVariables) {
            if (!variables.containsKey(variable) || variables.get(variable) == null) {
                throw new IllegalArgumentException(
                        "Missing prompt variable: " + variable);
            }
        }
    }

    /**
     * 读取并缓存模板。
     *
     * @param location Spring 资源位置
     * @return UTF-8 模板文本
     */
    private String load(String location) {
        return templateCache.computeIfAbsent(location, this::readResource);
    }

    /**
     * 从 Spring Resource 读取模板内容。
     *
     * @param location 资源位置
     * @return 模板文本
     */
    private String readResource(String location) {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load prompt resource: " + location,
                    exception);
        }
    }

    /**
     * 使用严格占位符语法替换模板变量。
     *
     * @param template 原始模板
     * @param variables 模板变量
     * @return 渲染文本
     */
    private String substitute(
            String template,
            Map<String, String> variables) {
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace(
                    "{{" + entry.getKey() + "}}",
                    entry.getValue());
        }
        if (rendered.matches("(?s).*\\{\\{[A-Za-z0-9_.-]+}}.*")) {
            throw new IllegalArgumentException(
                    "Prompt contains unresolved variables");
        }
        return rendered;
    }

    /**
     * 计算 Prompt 内容哈希。
     *
     * @param value 待摘要内容
     * @return 小写十六进制 SHA-256
     */
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
