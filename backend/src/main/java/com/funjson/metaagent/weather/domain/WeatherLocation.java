package com.funjson.metaagent.weather.domain;

/**
 * Resolved geographic location used by the weather provider.
 *
 * @param name city or place name
 * @param country country or region name
 * @param admin1 first-level administrative area
 * @param latitude latitude
 * @param longitude longitude
 * @param timezone IANA time-zone id
 */
public record WeatherLocation(
        String name,
        String country,
        String admin1,
        double latitude,
        double longitude,
        String timezone) {
}
