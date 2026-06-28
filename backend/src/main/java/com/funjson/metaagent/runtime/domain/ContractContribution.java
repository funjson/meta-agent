package com.funjson.metaagent.runtime.domain;

import java.util.List;
import java.util.Map;

/**
 * Skill 或父执行层对任务合同的补充。
 *
 * @param inputRequirements 输入要求
 * @param outputRequirements 输出要求
 * @param acceptanceRequirements 验收要求
 * @param evidenceRequirements Evidence 要求
 */
public record ContractContribution(
        Map<String, Object> inputRequirements,
        Map<String, Object> outputRequirements,
        List<String> acceptanceRequirements,
        List<String> evidenceRequirements) {

    /**
     * 复制可变集合，保证跨层合同不可变。
     */
    public ContractContribution {
        inputRequirements = inputRequirements == null
                ? Map.of()
                : Map.copyOf(inputRequirements);
        outputRequirements = outputRequirements == null
                ? Map.of()
                : Map.copyOf(outputRequirements);
        acceptanceRequirements = acceptanceRequirements == null
                ? List.of()
                : List.copyOf(acceptanceRequirements);
        evidenceRequirements = evidenceRequirements == null
                ? List.of()
                : List.copyOf(evidenceRequirements);
    }

    /**
     * @return 不补充任何要求的合同
     */
    public static ContractContribution empty() {
        return new ContractContribution(
                Map.of(),
                Map.of(),
                List.of(),
                List.of());
    }
}
