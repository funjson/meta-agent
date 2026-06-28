package com.funjson.metaagent.runtime.domain;

import java.util.Map;
import java.util.UUID;

/**
 * 子 Job 完成后回传给 origin LoopNode 的动作结果。
 *
 * @param childJobId 子 Job ID
 * @param status 子 Job 终态
 * @param resultSummary 结果摘要
 * @param outputs 结构化输出
 * @param evidenceCount Evidence 数量
 */
public record ChildJobOutcome(
        UUID childJobId,
        Status status,
        String resultSummary,
        Map<String, Object> outputs,
        int evidenceCount) {

    /**
     * 校验回传结果。
     */
    public ChildJobOutcome {
        if (childJobId == null || status == null) {
            throw new IllegalArgumentException(
                    "Child Job identity and status are required");
        }
        resultSummary = resultSummary == null ? "" : resultSummary;
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
        if (evidenceCount < 0) {
            throw new IllegalArgumentException(
                    "Evidence count cannot be negative");
        }
    }

    /**
     * 子 Job 可回传的终态。
     */
    public enum Status {
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
