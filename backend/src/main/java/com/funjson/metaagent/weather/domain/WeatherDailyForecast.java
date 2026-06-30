package com.funjson.metaagent.weather.domain;

import java.time.LocalDate;

/**
 * One day of weather forecast.
 *
 * @param date forecast date
 * @param condition human-readable condition
 * @param maxTemperatureCelsius daily maximum temperature
 * @param minTemperatureCelsius daily minimum temperature
 * @param precipitationProbabilityMaxPercent daily maximum precipitation chance
 */
public record WeatherDailyForecast(
        LocalDate date,
        String condition,
        double maxTemperatureCelsius,
        double minTemperatureCelsius,
        int precipitationProbabilityMaxPercent) {
}
