package com.funjson.metaagent.weather.domain;

import java.time.Instant;
import java.util.List;

/**
 * Complete weather tool result.
 *
 * @param location resolved provider location
 * @param fetchedAt fetch timestamp
 * @param timezone provider forecast time-zone
 * @param current current observation
 * @param daily daily forecast rows
 */
public record WeatherForecast(
        WeatherLocation location,
        Instant fetchedAt,
        String timezone,
        WeatherCurrent current,
        List<WeatherDailyForecast> daily) {

    /**
     * Defensively copies daily forecasts.
     */
    public WeatherForecast {
        daily = daily == null ? List.of() : List.copyOf(daily);
    }
}
