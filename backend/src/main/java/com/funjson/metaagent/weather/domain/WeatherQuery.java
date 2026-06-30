package com.funjson.metaagent.weather.domain;

/**
 * Structured weather query produced by the model tool call.
 *
 * @param location natural-language location, for example "北京"
 * @param forecastDays number of forecast days to return
 * @param locale response language preference
 */
public record WeatherQuery(
        String location,
        int forecastDays,
        String locale) {

    private static final int MIN_FORECAST_DAYS = 1;
    private static final int MAX_FORECAST_DAYS = 7;

    /**
     * Normalizes user-controlled query arguments before provider dispatch.
     */
    public WeatherQuery {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Weather location is required");
        }
        location = location.trim();
        forecastDays = Math.max(
                MIN_FORECAST_DAYS,
                Math.min(MAX_FORECAST_DAYS, forecastDays));
        locale = locale == null || locale.isBlank()
                ? "zh-CN"
                : locale.trim();
    }
}
