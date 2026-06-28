package com.funjson.metaagent.web.api.runtime;

import com.funjson.metaagent.runtime.api.AuthorizationDecisionRequest;
import com.funjson.metaagent.runtime.api.AuthorizationRequestView;
import com.funjson.metaagent.runtime.application.AuthorizationRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 提供待授权请求查询、批准和拒绝接口。
 */
@RestController
@RequestMapping("/api/v1/authorization-requests")
public class AuthorizationRequestController {

    private final AuthorizationRequestService service;

    /** 创建授权 Controller。 */
    public AuthorizationRequestController(
            AuthorizationRequestService service) {
        this.service = service;
    }

    /** 查询授权请求。 */
    @GetMapping
    public List<AuthorizationRequestView> list(
            @RequestParam(defaultValue = "PENDING") String status) {
        return service.list(status);
    }

    /** 批准授权请求。 */
    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable UUID id,
            @Valid @RequestBody AuthorizationDecisionRequest request) {
        service.approve(id, request.summary());
        return ResponseEntity.noContent().build();
    }

    /** 拒绝授权请求。 */
    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable UUID id,
            @Valid @RequestBody AuthorizationDecisionRequest request) {
        service.reject(id, request.summary());
        return ResponseEntity.noContent().build();
    }
}
