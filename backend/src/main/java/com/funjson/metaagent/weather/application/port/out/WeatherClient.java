package com.funjson.metaagent.weather.application.port.out;

import com.funjson.metaagent.weather.domain.WeatherForecast;
import com.funjson.metaagent.weather.domain.WeatherQuery;

/**
 * Outbound adapter port for weather providers.
 */
public interface WeatherClient {

    /**
     * Fetches current weather and short forecast.
     *
     * @param query structured weather query
     * @return provider forecast result
     */
    WeatherForecast forecast(WeatherQuery query);
}
