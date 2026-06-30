package com.funjson.metaagent.weather.infrastructure.openmeteo;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.runtime.domain.RuntimeStateException;
import com.funjson.metaagent.weather.application.port.out.WeatherClient;
import com.funjson.metaagent.weather.domain.WeatherCodeMapper;
import com.funjson.metaagent.weather.domain.WeatherCurrent;
import com.funjson.metaagent.weather.domain.WeatherDailyForecast;
import com.funjson.metaagent.weather.domain.WeatherForecast;
import com.funjson.metaagent.weather.domain.WeatherLocation;
import com.funjson.metaagent.weather.domain.WeatherQuery;
import org.springframework.stereotype.Component;

/**
 * Open-Meteo based weather provider adapter.
 */
@Component
public class OpenMeteoWeatherClient implements WeatherClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final URI DEFAULT_GEOCODING_BASE =
            URI.create("https://geocoding-api.open-meteo.com/v1/search");
    private static final URI DEFAULT_FORECAST_BASE =
            URI.create("https://api.open-meteo.com/v1/forecast");

    private final ObjectMapper objectMapper;
    private final URI geocodingBaseUri;
    private final URI forecastBaseUri;
    private final HttpClient httpClient;

    /**
     * Creates the Open-Meteo adapter with public API endpoints.
     *
     * @param objectMapper JSON mapper
     */
    public OpenMeteoWeatherClient(ObjectMapper objectMapper) {
        this(
                objectMapper,
                DEFAULT_GEOCODING_BASE,
                DEFAULT_FORECAST_BASE,
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build());
    }

    /**
     * Creates the adapter with explicit endpoints for tests.
     *
     * @param objectMapper JSON mapper
     * @param geocodingBaseUri geocoding endpoint
     * @param forecastBaseUri forecast endpoint
     * @param httpClient HTTP client
     */
    OpenMeteoWeatherClient(
            ObjectMapper objectMapper,
            URI geocodingBaseUri,
            URI forecastBaseUri,
            HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.geocodingBaseUri = geocodingBaseUri;
        this.forecastBaseUri = forecastBaseUri;
        this.httpClient = httpClient;
    }

    @Override
    public WeatherForecast forecast(WeatherQuery query) {
        WeatherLocation location = geocode(query);
        JsonNode forecast = getJson(forecastUri(location, query));
        return new WeatherForecast(
                location,
                Instant.now(),
                forecast.path("timezone").asText(location.timezone()),
                current(forecast.path("current")),
                daily(forecast.path("daily")));
    }

    /**
     * Resolves natural-language location to latitude/longitude.
     */
    private WeatherLocation geocode(WeatherQuery query) {
        JsonNode root = getJson(geocodingUri(query));
        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            throw new RuntimeStateException(
                    "WEATHER_LOCATION_NOT_FOUND",
                    "Weather location not found: " + query.location());
        }
        JsonNode first = results.get(0);
        return new WeatherLocation(
                first.path("name").asText(query.location()),
                first.path("country").asText(""),
                first.path("admin1").asText(""),
                first.path("latitude").asDouble(),
                first.path("longitude").asDouble(),
                first.path("timezone").asText("auto"));
    }

    /**
     * Converts the provider current block into the domain object.
     */
    private WeatherCurrent current(JsonNode current) {
        int weatherCode = current.path("weather_code").asInt();
        return new WeatherCurrent(
                current.path("time").asText(""),
                current.path("temperature_2m").asDouble(),
                current.path("apparent_temperature").asDouble(),
                current.path("relative_humidity_2m").asInt(),
                current.path("precipitation").asDouble(),
                current.path("wind_speed_10m").asDouble(),
                current.path("wind_direction_10m").asInt(),
                weatherCode,
                WeatherCodeMapper.condition(weatherCode));
    }

    /**
     * Converts daily forecast arrays into typed rows.
     */
    private List<WeatherDailyForecast> daily(JsonNode daily) {
        List<WeatherDailyForecast> forecasts = new ArrayList<>();
        JsonNode dates = daily.path("time");
        for (int index = 0; dates.isArray() && index < dates.size(); index++) {
            int weatherCode = daily.path("weather_code").path(index).asInt();
            forecasts.add(new WeatherDailyForecast(
                    LocalDate.parse(dates.get(index).asText()),
                    WeatherCodeMapper.condition(weatherCode),
                    daily.path("temperature_2m_max").path(index).asDouble(),
                    daily.path("temperature_2m_min").path(index).asDouble(),
                    daily.path("precipitation_probability_max")
                            .path(index)
                            .asInt()));
        }
        return forecasts;
    }

    /**
     * Builds the Open-Meteo geocoding URI.
     */
    private URI geocodingUri(WeatherQuery query) {
        String language = query.locale().startsWith("zh") ? "zh" : "en";
        return URI.create(geocodingBaseUri + "?name="
                + encode(query.location())
                + "&count=1&language="
                + language
                + "&format=json");
    }

    /**
     * Builds the Open-Meteo forecast URI.
     */
    private URI forecastUri(
            WeatherLocation location,
            WeatherQuery query) {
        return URI.create(forecastBaseUri
                + "?latitude=" + location.latitude()
                + "&longitude=" + location.longitude()
                + "&current=temperature_2m,relative_humidity_2m,"
                + "apparent_temperature,precipitation,weather_code,"
                + "wind_speed_10m,wind_direction_10m"
                + "&daily=weather_code,temperature_2m_max,"
                + "temperature_2m_min,precipitation_probability_max"
                + "&timezone=auto"
                + "&forecast_days=" + query.forecastDays());
    }

    /**
     * Executes an HTTP GET and parses the response JSON.
     */
    private JsonNode getJson(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "MetaAgentWeather/0.1")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeStateException(
                        "WEATHER_PROVIDER_FAILED",
                        "Weather provider returned HTTP "
                                + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new RuntimeStateException(
                    "WEATHER_PROVIDER_UNAVAILABLE",
                    "Weather provider unavailable: "
                            + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeStateException(
                    "WEATHER_PROVIDER_INTERRUPTED",
                    "Weather provider request was interrupted");
        }
    }

    /**
     * URL-encodes a query parameter.
     */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
