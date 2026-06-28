package com.funjson.metaagent.runtime.domain;

/**
 * Profile、Job、Task、Loop 或 Child Job 声明的一层策略。
 *
 * @param authority 权限包络
 * @param budget 预算
 * @param contract 合同要求
 */
public record PolicyLayer(
        AuthorityEnvelope authority,
        RuntimeBudget budget,
        ContractContribution contract) {

    /**
     * 校验策略层字段。
     */
    public PolicyLayer {
        if (authority == null || budget == null || contract == null) {
            throw new IllegalArgumentException(
                    "Policy layer fields are required");
        }
    }
}
