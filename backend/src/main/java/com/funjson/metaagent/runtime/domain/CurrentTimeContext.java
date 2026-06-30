package com.funjson.metaagent.runtime.domain;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Stable runtime fact that tells prompts how to interpret relative time words.
 *
 * @param now current zoned date-time
 * @param today local date for "today"
 * @param tomorrow local date for "tomorrow"
 * @param currentYear current local calendar year
 * @param zoneId IANA time-zone id
 */
public record CurrentTimeContext(
        ZonedDateTime now,
        LocalDate today,
        LocalDate tomorrow,
        int currentYear,
        String zoneId) {

    /**
     * Renders the time fact as a prompt block shared by Control and Loop.
     *
     * @return model-facing time context
     */
    public String promptText() {
        return """
                当前时间上下文（系统事实，不是用户消息）：
                - timezone: %s
                - now: %s
                - today: %s
                - tomorrow: %s
                - currentYear: %d

                规则：
                - 用户说“今天 / 现在 / 当前 / 最新 / 近期”时，必须以 today 和 now 为准。
                - 用户明确给出绝对日期、月份、年份或业务时间范围时，必须优先使用用户输入的时间；当前时间只用于解释相对时间和判断时效边界。
                - 如果用户给出的时间明显异常，例如年份位数异常，不要擅自修正；应保留原始表达或请求用户确认。
                - 不要凭空给查询或结论添加其他年份；只有用户明确给出年份时才保留。
                - 天气、新闻、价格、政策、版本等强时效问题必须优先使用当前时间上下文。
                """.formatted(
                zoneId,
                now,
                today,
                tomorrow,
                currentYear).trim();
    }
}
