package com.funjson.metaagent.weather.domain;

/**
 * Current weather observation.
 *
 * @param time provider-local observation time
 * @param temperatureCelsius air temperature in Celsius
 * @param apparentTemperatureCelsius apparent temperature in Celsius
 * @param relativeHumidityPercent relative humidity percentage
 * @param precipitationMm precipitation in millimeters
 * @param windSpeedKmh wind speed in kilometers per hour
 * @param windDirectionDegrees wind direction in degrees
 * @param weatherCode provider weather code
 * @param condition human-readable condition
 */
public record WeatherCurrent(
        String time,
        double temperatureCelsius,
        double apparentTemperatureCelsius,
        int relativeHumidityPercent,
        double precipitationMm,
        double windSpeedKmh,
        int windDirectionDegrees,
        int weatherCode,
        String condition) {
}
