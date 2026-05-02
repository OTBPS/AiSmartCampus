package com.smartcampus.navigation.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartcampus.navigation.common.BizException;
import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WeatherService {
    private static final BigDecimal CAMPUS_LONGITUDE = new BigDecimal("118.713437644448632");
    private static final BigDecimal CAMPUS_LATITUDE = new BigDecimal("32.203046572396190");
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final QWeatherClient qWeatherClient;
    private final PoiService poiService;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public WeatherService(QWeatherClient qWeatherClient, PoiService poiService) {
        this.qWeatherClient = qWeatherClient;
        this.poiService = poiService;
    }

    public WeatherResponse campus(Long poiId, String locale) {
        if (!qWeatherClient.isConfigured()) {
            throw new BizException("QWeather API key is not configured");
        }
        WeatherResponse.WeatherLocation location = resolveLocation(poiId);
        String lang = "en-US".equals(locale) ? "en" : "zh";
        String weatherLocation = location.longitude + "," + location.latitude;
        String cacheKey = (poiId == null ? "campus" : "poi:" + poiId) + "|" + weatherLocation + "|" + lang;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.response;
        }

        WeatherResponse response = buildResponse(location, weatherLocation, lang);
        cache.put(cacheKey, new CacheEntry(response, Instant.now().plus(CACHE_TTL)));
        return response;
    }

    private WeatherResponse buildResponse(WeatherResponse.WeatherLocation location, String weatherLocation, String lang) {
        JsonNode nowRoot = ensureOk(qWeatherClient.fetchNow(weatherLocation, lang));
        JsonNode hourlyRoot = ensureOk(qWeatherClient.fetchHourly24h(weatherLocation, lang));
        JsonNode dailyRoot = ensureOk(qWeatherClient.fetchDaily7d(weatherLocation, lang));

        WeatherResponse response = new WeatherResponse();
        response.location = location;
        response.updateTime = nowRoot.path("updateTime").asText("");
        response.now = parseNow(nowRoot.path("now"));
        response.hourly = parseHourly(hourlyRoot.path("hourly"));
        response.daily = parseDaily(dailyRoot.path("daily"));
        response.recommendations = recommendations(response, "en".equals(lang));
        return response;
    }

    private WeatherResponse.WeatherLocation resolveLocation(Long poiId) {
        WeatherResponse.WeatherLocation location = new WeatherResponse.WeatherLocation();
        if (poiId == null) {
            location.name = "NUIST Campus";
            location.longitude = coordinate(CAMPUS_LONGITUDE);
            location.latitude = coordinate(CAMPUS_LATITUDE);
            location.locationText = "NUIST central campus";
            location.category = "CAMPUS";
            return location;
        }

        PoiEntity poi = poiService.get(poiId);
        location.poiId = poi.id;
        location.name = poi.name;
        location.longitude = coordinate(poi.longitude);
        location.latitude = coordinate(poi.latitude);
        location.locationText = poi.locationText;
        location.category = poi.category;
        return location;
    }

    private String coordinate(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private JsonNode ensureOk(JsonNode root) {
        if (root == null) {
            throw new BizException("QWeather response is empty");
        }
        String code = root.path("code").asText("");
        if (!"200".equals(code)) {
            throw new BizException("QWeather request failed: " + (StringUtils.hasText(code) ? code : "unknown"));
        }
        return root;
    }

    private WeatherResponse.CurrentWeather parseNow(JsonNode node) {
        WeatherResponse.CurrentWeather now = new WeatherResponse.CurrentWeather();
        now.obsTime = node.path("obsTime").asText("");
        now.temp = intValue(node.path("temp"));
        now.feelsLike = intValue(node.path("feelsLike"));
        now.text = node.path("text").asText("");
        now.icon = node.path("icon").asText("");
        now.windDir = node.path("windDir").asText("");
        now.windScale = node.path("windScale").asText("");
        now.humidity = intValue(node.path("humidity"));
        now.precip = doubleValue(node.path("precip"));
        now.pressure = intValue(node.path("pressure"));
        now.vis = intValue(node.path("vis"));
        return now;
    }

    private List<WeatherResponse.HourlyWeather> parseHourly(JsonNode node) {
        List<WeatherResponse.HourlyWeather> items = new ArrayList<>();
        if (!node.isArray()) return items;
        for (JsonNode item : node) {
            WeatherResponse.HourlyWeather hourly = new WeatherResponse.HourlyWeather();
            hourly.fxTime = item.path("fxTime").asText("");
            hourly.temp = intValue(item.path("temp"));
            hourly.text = item.path("text").asText("");
            hourly.icon = item.path("icon").asText("");
            hourly.windDir = item.path("windDir").asText("");
            hourly.windScale = item.path("windScale").asText("");
            hourly.humidity = intValue(item.path("humidity"));
            hourly.pop = intValue(item.path("pop"));
            hourly.precip = doubleValue(item.path("precip"));
            items.add(hourly);
        }
        return items;
    }

    private List<WeatherResponse.DailyWeather> parseDaily(JsonNode node) {
        List<WeatherResponse.DailyWeather> items = new ArrayList<>();
        if (!node.isArray()) return items;
        for (JsonNode item : node) {
            WeatherResponse.DailyWeather daily = new WeatherResponse.DailyWeather();
            daily.fxDate = item.path("fxDate").asText("");
            daily.tempMax = intValue(item.path("tempMax"));
            daily.tempMin = intValue(item.path("tempMin"));
            daily.textDay = item.path("textDay").asText("");
            daily.textNight = item.path("textNight").asText("");
            daily.windDirDay = item.path("windDirDay").asText("");
            daily.windScaleDay = item.path("windScaleDay").asText("");
            daily.uvIndex = intValue(item.path("uvIndex"));
            daily.precip = doubleValue(item.path("precip"));
            items.add(daily);
        }
        return items;
    }

    private List<WeatherResponse.WeatherRecommendation> recommendations(WeatherResponse response, boolean english) {
        List<PoiEntity> pois = poiService.list(null, null, null, true, false, 100);
        List<WeatherResponse.WeatherRecommendation> items = new ArrayList<>();
        boolean wet = isWet(response);
        boolean hot = maxTemp(response) >= 32 || value(response.now.temp) >= 30;
        boolean cold = minTemp(response) <= 5 || value(response.now.temp) <= 5;
        boolean windy = maxWindScale(response) >= 5;
        boolean outdoorFriendly = !wet && !hot && !cold && !windy && value(response.now.temp) >= 12 && value(response.now.temp) <= 28;

        if (wet) {
            PoiEntity shelter = firstMatching(pois, "sheltered", "rain-friendly");
            items.add(recommendation("travel", english ? "Rain-friendly route" : "雨天路线提醒",
                    english ? "Prioritize sheltered corridors and leave extra walking time." : "优先选择有遮蔽的通道，并为步行预留更多时间。",
                    shelter, "warn"));
        }
        if (hot) {
            PoiEntity study = firstMatching(pois, "library", "quiet", "study");
            items.add(recommendation("study", english ? "Indoor study is safer" : "适合室内学习",
                    english ? "High temperature makes long outdoor stays uncomfortable; choose cool study spaces." : "气温偏高，长时间户外停留不舒适，建议选择凉爽的学习空间。",
                    study, "hot"));
        }
        if (cold || windy) {
            items.add(recommendation("travel", english ? "Reduce exposed walking" : "减少开阔区域步行",
                    english ? "Wind or low temperature can make open paths uncomfortable; prefer direct routes." : "风力或低温会让开阔路段体感更差，建议选择更直接的路线。",
                    null, "cool"));
        }
        if (outdoorFriendly) {
            PoiEntity outdoor = firstMatching(pois, "sports", "field", "observation", "landmark");
            items.add(recommendation("sport", english ? "Outdoor activity window" : "适合户外活动",
                    english ? "The next few hours look stable for outdoor exercise or campus observation." : "未来几小时天气较稳定，适合户外运动或校园观测。",
                    outdoor, "good"));
        }
        if (response.location.poiId != null) {
            items.add(recommendation("place", english ? "View this place on the AI map" : "回到 AI 地图查看地点",
                    english ? "Open the selected POI on the AI map to combine weather and navigation context." : "在 AI 地图中打开当前地点，把天气和导航场景结合起来。",
                    selectedPoi(response.location, pois), "map"));
        }
        if (items.isEmpty()) {
            items.add(recommendation("campus", english ? "Campus weather is steady" : "校园天气较平稳",
                    english ? "No strong weather constraint detected; plan routes by time and destination." : "暂未发现明显天气限制，可按时间和目的地正常规划路线。",
                    null, "good"));
        }
        return items;
    }

    private WeatherResponse.WeatherRecommendation recommendation(String type, String title, String detail, PoiEntity poi, String tone) {
        WeatherResponse.WeatherRecommendation item = new WeatherResponse.WeatherRecommendation();
        item.type = type;
        item.title = title;
        item.detail = detail;
        item.tone = tone;
        if (poi != null) {
            item.poiId = poi.id;
            item.poiName = poi.name;
        }
        return item;
    }

    private PoiEntity selectedPoi(WeatherResponse.WeatherLocation location, List<PoiEntity> pois) {
        return pois.stream().filter(poi -> poi.id.equals(location.poiId)).findFirst().orElseGet(() -> {
            PoiEntity poi = new PoiEntity();
            poi.id = location.poiId;
            poi.name = location.name;
            return poi;
        });
    }

    private PoiEntity firstMatching(List<PoiEntity> pois, String... needles) {
        return pois.stream()
                .filter(poi -> matches(poi, needles))
                .sorted(Comparator.comparing((PoiEntity poi) -> poi.mapRank == null ? 999 : poi.mapRank))
                .findFirst()
                .orElse(null);
    }

    private boolean matches(PoiEntity poi, String... needles) {
        String text = ((poi.name == null ? "" : poi.name) + " " + (poi.category == null ? "" : poi.category) + " " + (poi.tags == null ? "" : poi.tags)).toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (text.contains(needle) || ("sheltered".equals(needle) && Boolean.TRUE.equals(poi.sheltered))) {
                return true;
            }
        }
        return false;
    }

    private boolean isWet(WeatherResponse response) {
        if (weatherTextIsWet(response.now.text) || value(response.now.precip) > 0.0) return true;
        return response.hourly.stream().limit(6).anyMatch(item ->
                weatherTextIsWet(item.text) || value(item.precip) > 0.0 || value(item.pop) >= 50);
    }

    private boolean weatherTextIsWet(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return normalized.contains("雨") || normalized.contains("雪") || normalized.contains("rain")
                || normalized.contains("snow") || normalized.contains("shower") || normalized.contains("thunder");
    }

    private int maxTemp(WeatherResponse response) {
        return response.daily.stream().map(item -> value(item.tempMax)).max(Integer::compareTo).orElse(value(response.now.temp));
    }

    private int minTemp(WeatherResponse response) {
        return response.daily.stream().map(item -> value(item.tempMin)).min(Integer::compareTo).orElse(value(response.now.temp));
    }

    private int maxWindScale(WeatherResponse response) {
        int nowWind = windScale(response.now.windScale);
        int hourlyWind = response.hourly.stream().map(item -> windScale(item.windScale)).max(Integer::compareTo).orElse(0);
        int dailyWind = response.daily.stream().map(item -> windScale(item.windScaleDay)).max(Integer::compareTo).orElse(0);
        return Math.max(nowWind, Math.max(hourlyWind, dailyWind));
    }

    private int windScale(String value) {
        if (!StringUtils.hasText(value)) return 0;
        int max = 0;
        for (String part : value.split("[^0-9]+")) {
            if (StringUtils.hasText(part)) {
                max = Math.max(max, Integer.parseInt(part));
            }
        }
        return max;
    }

    private Integer intValue(JsonNode node) {
        String text = node.asText("");
        if (!StringUtils.hasText(text)) return null;
        try {
            return (int) Math.round(Double.parseDouble(text));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double doubleValue(JsonNode node) {
        String text = node.asText("");
        if (!StringUtils.hasText(text)) return null;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private double value(Double value) {
        return value == null ? 0.0 : value;
    }

    private static class CacheEntry {
        final WeatherResponse response;
        final Instant expiresAt;

        CacheEntry(WeatherResponse response, Instant expiresAt) {
            this.response = response;
            this.expiresAt = expiresAt;
        }
    }
}
