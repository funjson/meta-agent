package com.funjson.metaagent.web.api.profile;

import com.funjson.metaagent.profile.api.CreateSubagentProfileRequest;
import com.funjson.metaagent.profile.api.SubagentProfileView;
import com.funjson.metaagent.profile.application.SubagentProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SubagentProfile 配置 HTTP Adapter。
 */
@RestController
@RequestMapping("/api/v1/subagent-profiles")
public class SubagentProfileController {

    private final SubagentProfileService service;

    /**
     * 创建 SubagentProfile Controller。
     *
     * @param service Profile Service
     */
    public SubagentProfileController(SubagentProfileService service) {
        this.service = service;
    }

    /**
     * 创建不可变 Profile 版本。
     *
     * @param request 创建请求
     * @return Profile 视图
     */
    @PostMapping
    public SubagentProfileView create(
            @Valid @RequestBody CreateSubagentProfileRequest request) {
        return service.create(request);
    }

    /**
     * 查询 AgentProfile 的 SubagentProfile。
     *
     * @param agentProfileId AgentProfile ID
     * @return Profile 列表
     */
    @GetMapping
    public List<SubagentProfileView> list(
            @RequestParam String agentProfileId) {
        return service.list(agentProfileId);
    }
}
