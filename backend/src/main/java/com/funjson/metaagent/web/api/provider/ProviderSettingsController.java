package com.funjson.metaagent.web.api.provider;

import java.util.List;
import java.util.Map;

import com.funjson.metaagent.prompt.application.PromptRegistry;
import com.funjson.metaagent.prompt.domain.PromptUseCase;
import com.funjson.metaagent.provider.api.ProviderConfigView;
import com.funjson.metaagent.provider.api.ProviderTestResult;
import com.funjson.metaagent.provider.api.TestProviderRequest;
import com.funjson.metaagent.provider.api.UpdateProviderConfigRequest;
import com.funjson.metaagent.provider.application.ProviderConfigService;
import com.funjson.metaagent.provider.application.port.out.ProviderConnectionTestPort;
import com.funjson.metaagent.provider.domain.ModelRequest;
import com.funjson.metaagent.provider.domain.ModelResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供 Provider 配置和连接测试接口。
 */
@RestController
@RequestMapping("/api/v1/settings/providers")
public class ProviderSettingsController {

    private final ProviderConfigService configService;
    private final ProviderConnectionTestPort connectionTestPort;
    private final PromptRegistry promptRegistry;

    /**
     * 创建 Provider 设置 Controller。
     *
     * @param configService 配置服务
     * @param connectionTestPort Provider Connection Test Port
     * @param promptRegistry Prompt Registry
     */
    public ProviderSettingsController(
            ProviderConfigService configService,
            ProviderConnectionTestPort connectionTestPort,
            PromptRegistry promptRegistry) {
        this.configService = configService;
        this.connectionTestPort = connectionTestPort;
        this.promptRegistry = promptRegistry;
    }

    /**
     * 查询 Provider 配置。
     *
     * @return 配置列表
     */
    @GetMapping
    public List<ProviderConfigView> list() {
        return configService.list();
    }

    /**
     * 更新 Provider 配置。
     *
     * @param providerId Provider ID
     * @param request 更新请求
     * @return 更新后配置
     */
    @PutMapping("/{providerId}")
    public ProviderConfigView update(
            @PathVariable String providerId,
            @Valid @RequestBody UpdateProviderConfigRequest request) {
        return configService.update(providerId, request);
    }

    /**
     * 使用统一 Prompt 执行 Provider 连接测试。
     *
     * @param providerId Provider ID
     * @param request 测试请求
     * @return 测试结果
     */
    @PostMapping("/{providerId}/test")
    public ProviderTestResult test(
            @PathVariable String providerId,
            @Valid @RequestBody TestProviderRequest request) {
        if (!"deepseek".equals(providerId)) {
            throw new IllegalArgumentException("Unsupported provider test: " + providerId);
        }
        long started = System.nanoTime();
        var prompt = promptRegistry.render(
                PromptUseCase.PROVIDER_CONNECTION_TEST,
                Map.of());
        ModelResponse response = connectionTestPort.generate(new ModelRequest(
                null,
                null,
                "Provider connection test",
                prompt,
                32), request.apiKey());
        return new ProviderTestResult(
                true,
                response.provider(),
                response.model(),
                java.time.Duration.ofNanos(System.nanoTime() - started).toMillis(),
                "Connection verified");
    }
}
