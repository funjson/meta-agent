package com.funjson.metaagent.weather.application;

import com.funjson.metaagent.weather.application.port.out.WeatherClient;
import com.funjson.metaagent.weather.domain.WeatherForecast;
import com.funjson.metaagent.weather.domain.WeatherQuery;
import org.springframework.stereotype.Service;

/**
 * Application service that exposes weather as a stable framework capability.
 */
@Service
public class WeatherService {

    private final WeatherClient weatherClient;

    /**
     * Creates the weather service.
     *
     * @param weatherClient weather provider adapter
     */
    public WeatherService(WeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    /**
     * Returns current weather plus a bounded short forecast.
     *
     * @param query structured weather query
     * @return weather forecast
     */
    public WeatherForecast forecast(WeatherQuery query) {
        return weatherClient.forecast(query);
    }
}
