package com.funjson.metaagent.runtime.domain;

import java.util.List;

/**
 * 策略解析结果。
 *
 * @param status 决策状态
 * @param effectivePolicy 可立即生效的收窄策略
 * @param reasons 冲突或审批原因
 */
public record PolicyResolution(
        PolicyResolutionStatus status,
        EffectivePolicy effectivePolicy,
        List<String> reasons) {

    /**
     * 复制原因列表。
     */
    public PolicyResolution {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
