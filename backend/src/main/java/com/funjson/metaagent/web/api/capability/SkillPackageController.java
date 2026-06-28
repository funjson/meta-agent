package com.funjson.metaagent.web.api.capability;

import com.funjson.metaagent.capability.api.ImportSkillPackageRequest;
import com.funjson.metaagent.capability.api.SkillPackageView;
import com.funjson.metaagent.capability.application.SkillPackageImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SkillPackage 导入 HTTP Adapter。
 */
@RestController
@RequestMapping("/api/v1/skill-packages")
public class SkillPackageController {

    private final SkillPackageImportService importService;

    /**
     * 创建 SkillPackage Controller。
     *
     * @param importService 导入服务
     */
    public SkillPackageController(
            SkillPackageImportService importService) {
        this.importService = importService;
    }

    /**
     * 导入并激活一个不可变 SkillPackage 版本。
     *
     * @param request 导入请求
     * @return SkillPackage 视图
     */
    @PostMapping("/import")
    public SkillPackageView importPackage(
            @Valid @RequestBody ImportSkillPackageRequest request) {
        return importService.importPackage(request);
    }
}
