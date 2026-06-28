package com.funjson.metaagent.runtime.domain;

/**
 * 跨层引用一个不可变 SubagentProfile 版本。
 *
 * @param id SubagentProfile ID
 * @param version 版本
 */
public record SubagentProfileRef(String id, int version) {

    /**
     * 校验引用。
     */
    public SubagentProfileRef {
        if (id == null || id.isBlank() || version < 1) {
            throw new IllegalArgumentException(
                    "Valid SubagentProfile reference is required");
        }
    }
}
