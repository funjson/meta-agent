package com.funjson.metaagent.web.api.system;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
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

    /**
     * 创建系统信息接口。
     *
     * @param applicationName 应用名称
     */
    public SystemInfoController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
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
                "timestamp", Instant.now().toString());
    }
}
