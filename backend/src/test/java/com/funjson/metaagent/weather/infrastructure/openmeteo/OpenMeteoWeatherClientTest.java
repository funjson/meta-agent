package com.funjson.metaagent.weather.infrastructure.openmeteo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.weather.domain.WeatherQuery;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies Open-Meteo response parsing with a local HTTP server.
 */
class OpenMeteoWeatherClientTest {

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/geocode", exchange -> writeJson(
                exchange,
                """
                {
                  "results": [
                    {
                      "name": "Beijing",
                      "country": "China",
                      "admin1": "Beijing",
                      "latitude": 39.9,
                      "longitude": 116.4,
                      "timezone": "Asia/Shanghai"
                    }
                  ]
                }
                """));
        server.createContext("/forecast", exchange -> {
            assertThat(exchange.getRequestURI().getQuery())
                    .contains("latitude=39.9", "forecast_days=2");
            writeJson(
                    exchange,
                    """
                    {
                      "timezone": "Asia/Shanghai",
                      "current": {
                        "time": "2026-06-30T08:00",
                        "temperature_2m": 29.5,
                        "apparent_temperature": 31.0,
                        "relative_humidity_2m": 42,
                        "precipitation": 0,
                        "weather_code": 1,
                        "wind_speed_10m": 12.2,
                        "wind_direction_10m": 180
                      },
                      "daily": {
                        "time": ["2026-06-30", "2026-07-01"],
                        "weather_code": [1, 61],
                        "temperature_2m_max": [33.0, 30.0],
                        "temperature_2m_min": [22.0, 21.0],
                        "precipitation_probability_max": [10, 70]
                      }
                    }
                    """);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void parsesGeocodingAndForecastResponses() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        OpenMeteoWeatherClient client = new OpenMeteoWeatherClient(
                new ObjectMapper(),
                URI.create(base + "/geocode"),
                URI.create(base + "/forecast"),
                HttpClient.newHttpClient());

        var forecast = client.forecast(new WeatherQuery("北京", 2, "zh-CN"));

        assertThat(forecast.location().name()).isEqualTo("Beijing");
        assertThat(forecast.current().condition()).isEqualTo("晴间多云");
        assertThat(forecast.daily()).hasSize(2);
        assertThat(forecast.daily().get(1).condition()).isEqualTo("雨");
    }

    /**
     * Writes a JSON response.
     */
    private void writeJson(
            HttpExchange exchange,
            String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
