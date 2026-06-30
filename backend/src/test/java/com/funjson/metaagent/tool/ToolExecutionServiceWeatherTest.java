package com.funjson.metaagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funjson.metaagent.capability.application.CapabilityApplicationService;
import com.funjson.metaagent.clarification.application.ClarificationService;
import com.funjson.metaagent.file.application.FileAttachmentService;
import com.funjson.metaagent.tool.application.ToolExecutionService;
import com.funjson.metaagent.tool.application.ToolInvocationCommand;
import com.funjson.metaagent.tool.application.port.out.ToolStore;
import com.funjson.metaagent.websearch.application.WebSearchService;
import com.funjson.metaagent.websearch.application.port.out.WebResearchStore;
import com.funjson.metaagent.weather.application.WeatherService;
import com.funjson.metaagent.weather.domain.WeatherCurrent;
import com.funjson.metaagent.weather.domain.WeatherDailyForecast;
import com.funjson.metaagent.weather.domain.WeatherForecast;
import com.funjson.metaagent.weather.domain.WeatherLocation;
import com.funjson.metaagent.weather.domain.WeatherQuery;
import org.junit.jupiter.api.Test;

/**
 * Verifies the framework weather tool contract.
 */
class ToolExecutionServiceWeatherTest {

    @Test
    void weatherCurrentReturnsStructuredObservation() {
        ToolStore toolStore = mock(ToolStore.class);
        WeatherService weatherService = mock(WeatherService.class);
        ToolExecutionService service = new ToolExecutionService(
                toolStore,
                mock(CapabilityApplicationService.class),
                mock(ClarificationService.class),
                mock(FileAttachmentService.class),
                mock(WebSearchService.class),
                mock(WebResearchStore.class),
                weatherService,
                new ObjectMapper());

        when(toolStore.findInvocationByIdempotencyKey("weather-test"))
                .thenReturn(Optional.empty());
        when(weatherService.forecast(new WeatherQuery("北京", 2, "zh-CN")))
                .thenReturn(forecast());

        var view = service.invoke(new ToolInvocationCommand(
                "weather.current",
                Map.of(
                        "location", "北京",
                        "forecastDays", 2,
                        "locale", "zh-CN"),
                "weather-test",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()));

        assertThat(view.result()).containsEntry("toolId", "weather.current");
        assertThat(view.result()).containsEntry("toolType", "RETRIEVAL");
        assertThat(String.valueOf(view.result().get("stdout")))
                .contains("北京", "当前", "短期预报");
    }

    /**
     * Creates a deterministic weather forecast fixture.
     */
    private WeatherForecast forecast() {
        return new WeatherForecast(
                new WeatherLocation(
                        "北京",
                        "中国",
                        "北京",
                        39.9,
                        116.4,
                        "Asia/Shanghai"),
                Instant.parse("2026-06-30T00:00:00Z"),
                "Asia/Shanghai",
                new WeatherCurrent(
                        "2026-06-30T08:00",
                        29.5,
                        31.0,
                        42,
                        0,
                        12.2,
                        180,
                        1,
                        "晴间多云"),
                List.of(new WeatherDailyForecast(
                        LocalDate.parse("2026-06-30"),
                        "晴间多云",
                        33.0,
                        22.0,
                        10)));
    }
}
