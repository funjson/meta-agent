package com.funjson.metaagent.runtime.domain;

/**
 * PolicyResolver 计算出的不可变有效策略。
 *
 * @param authority 有效权限交集
 * @param budget 已分配预算
 * @param contract 已合并并加强的合同
 */
public record EffectivePolicy(
        AuthorityEnvelope authority,
        RuntimeBudget budget,
        ContractContribution contract) {
}
