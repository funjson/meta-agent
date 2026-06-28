package com.funjson.metaagent.runtime.domain;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * 可从父级剩余值向下分配的运行预算。
 *
 * @param maxDuration 最大运行时长
 * @param maxTokens 最大 Token 数
 * @param maxTasks 最大 Task 数
 * @param maxCost 最大成本
 */
public record RuntimeBudget(
        Duration maxDuration,
        long maxTokens,
        int maxTasks,
        BigDecimal maxCost) {

    /**
     * 校验预算不能为负数。
     */
    public RuntimeBudget {
        if (maxDuration == null
                || maxDuration.isNegative()
                || maxTokens < 0
                || maxTasks < 0
                || maxCost == null
                || maxCost.signum() < 0) {
            throw new IllegalArgumentException(
                    "Runtime budget values cannot be negative");
        }
    }

    /**
     * 按父级剩余值限制子级预算。
     *
     * @param requested 子级请求
     * @return 不超过父级的有效预算
     */
    public RuntimeBudget cap(RuntimeBudget requested) {
        return new RuntimeBudget(
                maxDuration.compareTo(requested.maxDuration) <= 0
                        ? maxDuration
                        : requested.maxDuration,
                Math.min(maxTokens, requested.maxTokens),
                Math.min(maxTasks, requested.maxTasks),
                maxCost.min(requested.maxCost));
    }

    /**
     * 判断请求是否超过当前预算。
     *
     * @param requested 子级请求
     * @return 是否需要额外预算
     */
    public boolean exceededBy(RuntimeBudget requested) {
        return requested.maxDuration.compareTo(maxDuration) > 0
                || requested.maxTokens > maxTokens
                || requested.maxTasks > maxTasks
                || requested.maxCost.compareTo(maxCost) > 0;
    }
}
