package com.funjson.metaagent.web.api.job;

import com.funjson.metaagent.job.api.CreateTaskGraphTemplateRequest;
import com.funjson.metaagent.job.api.TaskGraphTemplateView;
import com.funjson.metaagent.job.application.TaskGraphTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * 提供 TaskGraphTemplate 配置接口。
 */
@RestController
@RequestMapping("/api/v1/task-graph-templates")
public class TaskGraphTemplateController {

    private final TaskGraphTemplateService service;

    /**
     * 创建模板控制器。
     *
     * @param service 模板服务
     */
    public TaskGraphTemplateController(
            TaskGraphTemplateService service) {
        this.service = service;
    }

    /**
     * 创建并激活模板新版本。
     *
     * @param request 创建请求
     * @return 新版本
     */
    @PostMapping
    public ResponseEntity<TaskGraphTemplateView> create(
            @Valid @RequestBody CreateTaskGraphTemplateRequest request) {
        TaskGraphTemplateView created = service.createVersion(request);
        return ResponseEntity.created(URI.create(
                "/api/v1/task-graph-templates/"
                        + created.id()
                        + "/versions/"
                        + created.version())).body(created);
    }

    /**
     * 查询 Profile 下模板。
     *
     * @param agentProfileId AgentProfile ID
     * @return 模板版本
     */
    @GetMapping
    public List<TaskGraphTemplateView> list(
            @RequestParam String agentProfileId) {
        return service.list(agentProfileId);
    }

    /**
     * 查询指定版本。
     *
     * @param id 模板 ID
     * @param version 版本
     * @return 模板版本
     */
    @GetMapping("/{id}/versions/{version}")
    public TaskGraphTemplateView get(
            @PathVariable UUID id,
            @PathVariable int version) {
        return service.get(id, version);
    }
}
