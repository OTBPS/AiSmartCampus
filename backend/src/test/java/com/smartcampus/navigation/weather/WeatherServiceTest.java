package com.smartcampus.navigation.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeatherServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fallsBackToDemoWeatherWhenApiKeyIsMissing() {
        TestContext ctx = new TestContext();
        when(ctx.client.isConfigured()).thenReturn(false);

        WeatherResponse response = ctx.service().campus(null, "zh-CN");

        assertEquals("Demo Weather", response.source);
        assertEquals("NUIST Campus", response.location.name);
        assertEquals(24, response.hourly.size());
        assertEquals(7, response.daily.size());
        verify(ctx.client, never()).fetchNow("118.71,32.20", "zh");
    }

    @Test
    void usesCampusDefaultCoordinateWhenPoiIsAbsent() throws Exception {
        TestContext ctx = new TestContext();
        ctx.stubWeather("晴", "0", "10", "25", "18", "2");

        WeatherResponse response = ctx.service().campus(null, "zh-CN");

        assertEquals("NUIST Campus", response.location.name);
        assertEquals("118.71", response.location.longitude);
        assertEquals("32.20", response.location.latitude);
        verify(ctx.client).fetchNow("118.71,32.20", "zh");
    }

    @Test
    void usesPoiCoordinateWhenPoiIsSelected() throws Exception {
        TestContext ctx = new TestContext();
        ctx.stubWeather("晴", "0", "10", "25", "18", "2");

        WeatherResponse response = ctx.service().campus(20L, "en-US");

        assertEquals(20L, response.location.poiId);
        assertEquals("West Garden Observation Field", response.location.name);
        assertEquals("118.71", response.location.longitude);
        assertEquals("32.20", response.location.latitude);
        verify(ctx.client).fetchNow("118.71,32.20", "en");
    }

    @Test
    void parsesQWeatherResponse() throws Exception {
        TestContext ctx = new TestContext();
        ctx.stubWeather("小雨", "0.8", "80", "30", "22", "4");

        WeatherResponse response = ctx.service().campus(null, "zh-CN");

        assertEquals(24, response.now.temp);
        assertEquals("小雨", response.now.text);
        assertEquals(1, response.hourly.size());
        assertEquals(80, response.hourly.get(0).pop);
        assertEquals(1, response.daily.size());
        assertEquals(30, response.daily.get(0).tempMax);
    }

    @Test
    void cachesRepeatedRequest() throws Exception {
        TestContext ctx = new TestContext();
        ctx.stubWeather("晴", "0", "10", "25", "18", "2");
        WeatherService service = ctx.service();

        service.campus(null, "zh-CN");
        service.campus(null, "zh-CN");

        verify(ctx.client).fetchNow("118.71,32.20", "zh");
    }

    @Test
    void recommendsShelteredPoiOnRain() throws Exception {
        TestContext ctx = new TestContext();
        ctx.stubWeather("小雨", "0.8", "80", "28", "20", "3");

        WeatherResponse response = ctx.service().campus(null, "zh-CN");

        assertTrue(response.recommendations.stream().anyMatch(item -> "travel".equals(item.type) && item.poiId != null));
    }

    @Test
    void recommendsIndoorStudyOnHeat() throws Exception {
        TestContext ctx = new TestContext();
        ctx.stubWeather("晴", "0", "10", "35", "27", "3");

        WeatherResponse response = ctx.service().campus(null, "zh-CN");

        assertTrue(response.recommendations.stream().anyMatch(item -> "study".equals(item.type)));
    }

    @Test
    void recommendsOutdoorActivityWhenWeatherIsStable() throws Exception {
        TestContext ctx = new TestContext();
        ctx.stubWeather("晴", "0", "10", "25", "18", "2");

        WeatherResponse response = ctx.service().campus(null, "zh-CN");

        assertTrue(response.recommendations.stream().anyMatch(item -> "sport".equals(item.type)));
    }

    private class TestContext {
        final QWeatherClient client = mock(QWeatherClient.class);
        final PoiService poiService = mock(PoiService.class);

        TestContext() {
            when(client.isConfigured()).thenReturn(true);
            when(poiService.get(20L)).thenReturn(poi(20L, "West Garden Observation Field", "LANDMARK", false, "meteorology,observation,field", 20));
            when(poiService.list(null, null, null, true, false, 100)).thenReturn(List.of(
                    poi(1L, "NUIST Library Study Area", "STUDY", true, "library,quiet,study,sheltered", 1),
                    poi(20L, "West Garden Observation Field", "LANDMARK", false, "meteorology,observation,field", 20),
                    poi(88L, "Central Track and Field Ground", "SPORTS", false, "sports,track,field", 30)
            ));
        }

        WeatherService service() {
            return new WeatherService(client, poiService);
        }

        void stubWeather(String text, String precip, String pop, String tempMax, String tempMin, String windScale) throws Exception {
            when(client.fetchNow("118.71,32.20", "zh")).thenReturn(now(text, precip, windScale));
            when(client.fetchHourly24h("118.71,32.20", "zh")).thenReturn(hourly(text, precip, pop, windScale));
            when(client.fetchDaily7d("118.71,32.20", "zh")).thenReturn(daily(text, tempMax, tempMin, windScale));
            when(client.fetchNow("118.71,32.20", "en")).thenReturn(now(text, precip, windScale));
            when(client.fetchHourly24h("118.71,32.20", "en")).thenReturn(hourly(text, precip, pop, windScale));
            when(client.fetchDaily7d("118.71,32.20", "en")).thenReturn(daily(text, tempMax, tempMin, windScale));
        }

        PoiEntity poi(Long id, String name, String category, boolean sheltered, String tags, Integer mapRank) {
            PoiEntity poi = new PoiEntity();
            poi.id = id;
            poi.name = name;
            poi.category = category;
            poi.longitude = new BigDecimal("118.713437644448632");
            poi.latitude = new BigDecimal("32.203046572396190");
            poi.locationText = "NUIST";
            poi.tags = tags;
            poi.sheltered = sheltered;
            poi.mapRank = mapRank;
            return poi;
        }
    }

    private JsonNode now(String text, String precip, String windScale) throws Exception {
        return objectMapper.readTree("""
                {
                  "code": "200",
                  "updateTime": "2026-05-02T10:00+08:00",
                  "now": {
                    "obsTime": "2026-05-02T10:00+08:00",
                    "temp": "24",
                    "feelsLike": "25",
                    "text": "%s",
                    "icon": "100",
                    "windDir": "东风",
                    "windScale": "%s",
                    "humidity": "65",
                    "precip": "%s",
                    "pressure": "1010",
                    "vis": "12"
                  }
                }
                """.formatted(text, windScale, precip));
    }

    private JsonNode hourly(String text, String precip, String pop, String windScale) throws Exception {
        return objectMapper.readTree("""
                {
                  "code": "200",
                  "hourly": [
                    {
                      "fxTime": "2026-05-02T11:00+08:00",
                      "temp": "25",
                      "text": "%s",
                      "icon": "100",
                      "windDir": "东风",
                      "windScale": "%s",
                      "humidity": "63",
                      "pop": "%s",
                      "precip": "%s"
                    }
                  ]
                }
                """.formatted(text, windScale, pop, precip));
    }

    private JsonNode daily(String text, String tempMax, String tempMin, String windScale) throws Exception {
        return objectMapper.readTree("""
                {
                  "code": "200",
                  "daily": [
                    {
                      "fxDate": "2026-05-02",
                      "tempMax": "%s",
                      "tempMin": "%s",
                      "textDay": "%s",
                      "textNight": "%s",
                      "windDirDay": "东风",
                      "windScaleDay": "%s",
                      "uvIndex": "5",
                      "precip": "0"
                    }
                  ]
                }
                """.formatted(tempMax, tempMin, text, text, windScale));
    }
}
