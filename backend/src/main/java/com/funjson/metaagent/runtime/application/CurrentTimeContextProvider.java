package com.funjson.metaagent.runtime.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import com.funjson.metaagent.runtime.domain.CurrentTimeContext;
import org.springframework.stereotype.Service;

/**
 * Provides a single runtime time fact for intent recognition and Loop prompts.
 */
@Service
public class CurrentTimeContextProvider {

    private final Clock clock;

    /**
     * Creates the provider using the JVM default time zone.
     */
    public CurrentTimeContextProvider() {
        this(Clock.systemDefaultZone());
    }

    /**
     * Creates the provider with an explicit clock for deterministic tests.
     *
     * @param clock runtime clock
     */
    public CurrentTimeContextProvider(Clock clock) {
        this.clock = clock;
    }

    /**
     * Captures the current time once for a prompt-rendering boundary.
     *
     * @return immutable current time context
     */
    public CurrentTimeContext current() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        return new CurrentTimeContext(
                now,
                today,
                today.plusDays(1),
                today.getYear(),
                now.getZone().getId());
    }
}
