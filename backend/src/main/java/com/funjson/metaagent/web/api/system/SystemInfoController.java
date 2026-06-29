package com.funjson.metaagent.web.api.system;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 暴露不包含密钥和运行时敏感信息的系统元数据。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

    private final String applicationName;
    private final String chatResponseMode;

    /**
     * 创建系统信息接口。
     *
     * @param applicationName 应用名称
     */
    @Autowired
    public SystemInfoController(
            @Value("${spring.application.name}") String applicationName,
            @Value("${meta-agent.chat.response-mode:sse-replay}")
            String chatResponseMode) {
        this.applicationName = applicationName;
        this.chatResponseMode = chatResponseMode;
    }

    /**
     * Test-compatible constructor using the default UI stream mode.
     *
     * @param applicationName application name
     */
    public SystemInfoController(String applicationName) {
        this(applicationName, "sse-replay");
    }

    /**
     * 返回前端启动探测所需的最小系统信息。
     *
     * @return 系统信息
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "application", applicationName,
                "version", "0.1.0-SNAPSHOT",
                "status", "READY",
                "chatTransport", Map.of(
                        "responseMode", chatResponseMode,
                        "streamingEnabled", "sse-replay".equals(chatResponseMode)),
                "timestamp", Instant.now().toString());
    }
}
