package com.smartcampus.navigation.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcampus.navigation.common.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class QWeatherClient {
    private final String apiKey;
    private final String baseUrl;
    private final int timeoutSeconds;
    private final ObjectMapper objectMapper;

    public QWeatherClient(
            @Value("${app.weather.qweather.api-key:}") String apiKey,
            @Value("${app.weather.qweather.base-url:https://nc2pg7kpbt.re.qweatherapi.com}") String baseUrl,
            @Value("${app.weather.qweather.timeout-seconds:8}") int timeoutSeconds,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    public JsonNode fetchNow(String location, String lang) {
        return fetch("/v7/weather/now", location, lang);
    }

    public JsonNode fetchHourly24h(String location, String lang) {
        return fetch("/v7/weather/24h", location, lang);
    }

    public JsonNode fetchDaily7d(String location, String lang) {
        return fetch("/v7/weather/7d", location, lang);
    }

    private JsonNode fetch(String path, String location, String lang) {
        byte[] body = client().get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam("location", location)
                        .queryParam("lang", lang)
                        .queryParam("unit", "m")
                        .build())
                .retrieve()
                .body(byte[].class);
        String text = decodeBody(body);
        if (!StringUtils.hasText(text)) {
            throw new BizException("QWeather response is empty");
        }
        try {
            return objectMapper.readTree(text);
        } catch (JsonProcessingException ex) {
            throw new BizException("QWeather response parse failed");
        }
    }

    private String decodeBody(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        if (body.length >= 2 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
                return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new BizException("QWeather response decompress failed");
            }
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-QW-Api-Key", apiKey)
                .build();
    }
}
