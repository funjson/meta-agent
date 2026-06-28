package com.funjson.metaagent.runtime.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户授权决定。
 *
 * @param summary 决定摘要
 */
public record AuthorizationDecisionRequest(
        @NotBlank String summary) {
}
