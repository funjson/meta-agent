package com.funjson.metaagent.intent.domain;

import java.util.List;

/**
 * Describes task-level intent rewriting applied during turn understanding.
 *
 * <p>The rewrite normalizes a casual user expression into a clear Job goal. It
 * must not invent missing facts or create tool-specific search queries.</p>
 *
 * @param changed whether the canonical goal differs materially from the source
 * @param summary auditable rewrite explanation
 * @param preservedUserFacts user facts explicitly preserved by the rewrite
 */
public record IntentRewrite(
        boolean changed,
        String summary,
        List<String> preservedUserFacts) {

    /**
     * Normalizes nullable fields.
     */
    public IntentRewrite {
        summary = summary == null ? "" : summary.trim();
        preservedUserFacts = preservedUserFacts == null
                ? List.of()
                : List.copyOf(preservedUserFacts);
    }

    /**
     * @return empty rewrite metadata
     */
    public static IntentRewrite none() {
        return new IntentRewrite(false, "", List.of());
    }
}
