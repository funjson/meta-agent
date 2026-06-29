package com.funjson.metaagent.web.api.provider;

import com.funjson.metaagent.provider.api.ModelCatalogView;
import com.funjson.metaagent.provider.application.ModelCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供模型能力目录 API。
 */
@RestController
@RequestMapping("/api/v1/settings/models")
public class ModelSettingsController {

    private final ModelCatalogService modelCatalog;

    /**
     * 创建模型设置 Controller。
     *
     * @param modelCatalog 模型目录
     */
    public ModelSettingsController(ModelCatalogService modelCatalog) {
        this.modelCatalog = modelCatalog;
    }

    /**
     * 查询框架支持的模型能力目录。
     *
     * @return 模型目录
     */
    @GetMapping
    public ModelCatalogView list() {
        return modelCatalog.view();
    }
}
