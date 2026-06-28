package com.funjson.metaagent.runtime;

import com.funjson.metaagent.runtime.domain.AuthorityEnvelope;
import com.funjson.metaagent.runtime.domain.ContractContribution;
import com.funjson.metaagent.runtime.domain.PolicyLayer;
import com.funjson.metaagent.runtime.domain.PolicyResolutionStatus;
import com.funjson.metaagent.runtime.domain.PolicyResolver;
import com.funjson.metaagent.runtime.domain.RuntimeBudget;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证策略继承只能收窄，并正确区分拒绝、审批和人工冲突。
 */
class PolicyResolverTest {

    private final PolicyResolver resolver = new PolicyResolver();

    @Test
    void narrowsAuthorityAndMergesAcceptanceRequirements() {
        var result = resolver.resolve(
                layer(
                        Set.of("deepseek", "fake"),
                        Set.of("search", "file"),
                        Set.of(),
                        budget(10_000, 6, "10"),
                        Map.of(),
                        List.of("parent acceptance")),
                layer(
                        Set.of("deepseek"),
                        Set.of("search"),
                        Set.of(),
                        budget(4_000, 2, "3"),
                        Map.of(),
                        List.of("child acceptance")));

        assertThat(result.status())
                .isEqualTo(PolicyResolutionStatus.ALLOWED);
        assertThat(result.effectivePolicy().authority().allowedProviders())
                .containsExactly("deepseek");
        assertThat(result.effectivePolicy()
                .contract()
                .acceptanceRequirements())
                .containsExactly(
                        "parent acceptance",
                        "child acceptance");
    }

    @Test
    void waitsForApprovalWhenDelegableCapabilityOrBudgetIsRequested() {
        var result = resolver.resolve(
                layer(
                        Set.of("fake"),
                        Set.of(),
                        Set.of("provider:deepseek"),
                        budget(1_000, 1, "1"),
                        Map.of(),
                        List.of()),
                layer(
                        Set.of("deepseek"),
                        Set.of(),
                        Set.of(),
                        budget(2_000, 2, "2"),
                        Map.of(),
                        List.of()));

        assertThat(result.status())
                .isEqualTo(PolicyResolutionStatus.WAITING_APPROVAL);
    }

    @Test
    void rejectsNonDelegableAuthorityAndFlagsInputContractConflict() {
        var rejected = resolver.resolve(
                layer(
                        Set.of("fake"),
                        Set.of(),
                        Set.of(),
                        budget(1_000, 1, "1"),
                        Map.of(),
                        List.of()),
                layer(
                        Set.of("deepseek"),
                        Set.of(),
                        Set.of(),
                        budget(500, 1, "1"),
                        Map.of(),
                        List.of()));
        assertThat(rejected.status())
                .isEqualTo(PolicyResolutionStatus.REJECTED);

        var human = resolver.resolve(
                layer(
                        Set.of("fake"),
                        Set.of(),
                        Set.of(),
                        budget(1_000, 1, "1"),
                        Map.of("format", "json"),
                        List.of()),
                layer(
                        Set.of("fake"),
                        Set.of(),
                        Set.of(),
                        budget(500, 1, "1"),
                        Map.of("format", "pdf"),
                        List.of()));
        assertThat(human.status())
                .isEqualTo(PolicyResolutionStatus.WAITING_HUMAN);
    }

    /** 创建策略层。 */
    private PolicyLayer layer(
            Set<String> providers,
            Set<String> tools,
            Set<String> delegable,
            RuntimeBudget budget,
            Map<String, Object> inputs,
            List<String> acceptance) {
        return new PolicyLayer(
                new AuthorityEnvelope(
                        providers,
                        tools,
                        Set.of(),
                        Set.of(),
                        delegable),
                budget,
                new ContractContribution(
                        inputs,
                        Map.of(),
                        acceptance,
                        List.of()));
    }

    /** 创建测试预算。 */
    private RuntimeBudget budget(
            long tokens,
            int tasks,
            String cost) {
        return new RuntimeBudget(
                Duration.ofMinutes(10),
                tokens,
                tasks,
                new BigDecimal(cost));
    }
}
