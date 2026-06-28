package com.funjson.metaagent.runtime.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 计算 `Profile → Job → Task → Loop → Child Job` 的有效策略。
 */
public class PolicyResolver {

    /**
     * 在父级有效策略上应用子级策略。
     *
     * @param parent 父级策略
     * @param requested 子级声明
     * @return 有效策略与冲突状态
     */
    public PolicyResolution resolve(
            PolicyLayer parent,
            PolicyLayer requested) {
        List<String> reasons = new ArrayList<>();
        PolicyResolutionStatus authorityStatus =
                authorityStatus(parent.authority(), requested.authority(), reasons);
        boolean contractConflict = hasContractConflict(
                parent.contract().inputRequirements(),
                requested.contract().inputRequirements());
        if (contractConflict) {
            reasons.add("输入合同存在不兼容要求");
        }
        boolean budgetExceeded =
                parent.budget().exceededBy(requested.budget());
        if (budgetExceeded) {
            reasons.add("请求预算超过父级剩余预算");
        }

        PolicyResolutionStatus status = authorityStatus;
        if (status != PolicyResolutionStatus.REJECTED
                && contractConflict) {
            status = PolicyResolutionStatus.WAITING_HUMAN;
        } else if (status == PolicyResolutionStatus.ALLOWED
                && budgetExceeded) {
            status = PolicyResolutionStatus.WAITING_APPROVAL;
        }
        return new PolicyResolution(
                status,
                new EffectivePolicy(
                        parent.authority().narrow(requested.authority()),
                        parent.budget().cap(requested.budget()),
                        mergeContracts(
                                parent.contract(),
                                requested.contract())),
                reasons);
    }

    /** 计算权限差异的处理状态。 */
    private PolicyResolutionStatus authorityStatus(
            AuthorityEnvelope parent,
            AuthorityEnvelope requested,
            List<String> reasons) {
        Set<String> missing = new LinkedHashSet<>();
        requested.allowedProviders().stream()
                .filter(value -> !parent.allowedProviders().contains(value))
                .map(value -> "provider:" + value)
                .forEach(missing::add);
        requested.allowedTools().stream()
                .filter(value -> !parent.allowedTools().contains(value))
                .map(value -> "tool:" + value)
                .forEach(missing::add);
        requested.dataScopes().stream()
                .filter(value -> !parent.dataScopes().contains(value))
                .map(value -> "data:" + value)
                .forEach(missing::add);
        requested.fileScopes().stream()
                .filter(value -> !parent.fileScopes().contains(value))
                .map(value -> "file:" + value)
                .forEach(missing::add);
        if (missing.isEmpty()) {
            return PolicyResolutionStatus.ALLOWED;
        }
        if (parent.delegableCapabilities().containsAll(missing)) {
            reasons.add("请求包含需要用户批准的可委托能力: " + missing);
            return PolicyResolutionStatus.WAITING_APPROVAL;
        }
        reasons.add("请求突破不可委托安全边界: " + missing);
        return PolicyResolutionStatus.REJECTED;
    }

    /** 判断输入合同同键值冲突。 */
    private boolean hasContractConflict(
            Map<String, Object> parent,
            Map<String, Object> child) {
        return child.entrySet().stream()
                .anyMatch(entry -> parent.containsKey(entry.getKey())
                        && !java.util.Objects.equals(
                                parent.get(entry.getKey()),
                                entry.getValue()));
    }

    /** 合并合同并保留父级要求。 */
    private ContractContribution mergeContracts(
            ContractContribution parent,
            ContractContribution child) {
        Map<String, Object> inputs =
                new LinkedHashMap<>(parent.inputRequirements());
        inputs.putAll(child.inputRequirements());
        Map<String, Object> outputs =
                new LinkedHashMap<>(parent.outputRequirements());
        outputs.putAll(child.outputRequirements());
        List<String> acceptance = union(
                parent.acceptanceRequirements(),
                child.acceptanceRequirements());
        List<String> evidence = union(
                parent.evidenceRequirements(),
                child.evidenceRequirements());
        return new ContractContribution(
                inputs,
                outputs,
                acceptance,
                evidence);
    }

    /** 合并并去重有序要求。 */
    private List<String> union(
            List<String> left,
            List<String> right) {
        Set<String> values = new LinkedHashSet<>(left);
        values.addAll(right);
        return List.copyOf(values);
    }
}
