package com.funjson.metaagent.capability;

import com.funjson.metaagent.capability.domain.SkillExecutable;
import com.funjson.metaagent.capability.domain.SkillResource;
import com.funjson.metaagent.capability.domain.SkillResourceType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 SkillPackage 资源路径和脚本 executable 边界。
 */
class SkillResourceTest {

    @Test
    void rejectsPathEscapingPackage() {
        assertThatThrownBy(() -> new SkillResource(
                "../secret.txt",
                SkillResourceType.REFERENCE,
                "secret",
                "hash",
                null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsScriptWithExplicitToolMetadata() {
        SkillResource resource = new SkillResource(
                "scripts/run.ps1",
                SkillResourceType.SCRIPT,
                "Write-Output ok",
                "hash",
                new SkillExecutable(
                        "skill.run",
                        "powershell",
                        "scripts/run.ps1",
                        Map.of(),
                        "NONE"));

        assertThat(resource.executable().toolId())
                .isEqualTo("skill.run");
    }
}
