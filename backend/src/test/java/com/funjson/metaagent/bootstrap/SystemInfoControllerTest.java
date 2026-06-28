package com.funjson.metaagent.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.funjson.metaagent.web.api.system.SystemInfoController;
import org.junit.jupiter.api.Test;

/**
 * 验证系统信息接口不会暴露 Secret。
 */
class SystemInfoControllerTest {

    @Test
    void exposesNonSecretRuntimeMetadata() {
        SystemInfoController controller = new SystemInfoController("meta-agent");

        Map<String, Object> result = controller.info();

        assertThat(result)
                .containsEntry("application", "meta-agent")
                .containsEntry("status", "READY")
                .doesNotContainKeys("apiKey", "secret", "authorization");
    }
}
