package com.smartcampus.navigation.ai;

import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiService;
import com.smartcampus.navigation.weather.WeatherResponse;
import com.smartcampus.navigation.weather.WeatherService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RouteWeatherEnhancer {
    private static final int MAX_SHELTER_CANDIDATES = 5;
    private static final double MAX_ROUTE_DISTANCE_METERS = 50.0;
    private static final double SAME_POINT_DISTANCE_METERS = 5.0;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320.0;

    private final WeatherService weatherService;
    private final PoiService poiService;

    public RouteWeatherEnhancer(WeatherService weatherService, PoiService poiService) {
        this.weatherService = weatherService;
        this.poiService = poiService;
    }

    public AiChatResponse enhance(AiChatResponse response, String locale) {
        if (response == null || response.mapActions == null) {
            return response;
        }
        AiChatResponse.MapAction routeAction = response.mapActions.stream()
                .filter(action -> "draw_route".equals(action.type))
                .findFirst()
                .orElse(null);
        if (routeAction == null || routeAction.poiIds == null || routeAction.poiIds.size() < 2) {
            return response;
        }

        Map<String, Object> payload = mutablePayload(routeAction);
        payload.putIfAbsent("weatherAdjusted", false);
        payload.putIfAbsent("shelterWaypointIds", List.of());
        payload.putIfAbsent("originalPoiIds", List.copyOf(routeAction.poiIds));
        try {
            WeatherResponse weather = weatherService.campus(null, locale);
            boolean english = "en-US".equals(locale);
            String weatherReason = severeWeatherReason(weather, english);
            if (!StringUtils.hasText(weatherReason)) {
                return response;
            }
            List<PoiEntity> allPois = poiService.list(null, null, null, true);
            ShelterSuggestions suggestions = shelterSuggestions(routeAction.poiIds, allPois == null ? List.of() : allPois);
            if (suggestions.candidates.isEmpty()) {
                return response;
            }
            applyShelterSuggestions(response, routeAction, payload, suggestions, weatherReason, english);
        } catch (RuntimeException ex) {
            payload.put("weatherAdjusted", false);
        }
        return response;
    }

    private Map<String, Object> mutablePayload(AiChatResponse.MapAction routeAction) {
        if (routeAction.payload == null) {
            routeAction.payload = new LinkedHashMap<>();
        } else if (!(routeAction.payload instanceof LinkedHashMap)) {
            routeAction.payload = new LinkedHashMap<>(routeAction.payload);
        }
        return routeAction.payload;
    }

    private ShelterSuggestions shelterSuggestions(List<Long> originalIds, List<PoiEntity> allPois) {
        Map<Long, PoiEntity> byId = new LinkedHashMap<>();
        for (PoiEntity poi : allPois) {
            if (poi != null && poi.id != null) {
                byId.put(poi.id, poi);
            }
        }
        List<PoiEntity> routePois = originalIds.stream()
                .map(byId::get)
                .filter(this::hasCoordinate)
                .toList();
        if (routePois.size() < 2) {
            return new ShelterSuggestions(routePois, List.of());
        }

        Set<Long> routeIds = new LinkedHashSet<>(originalIds);
        RouteProjection projection = RouteProjection.from(routePois);
        List<ShelterCandidate> candidates = allPois.stream()
                .filter(this::hasCoordinate)
                .filter(this::isSheltered)
                .filter(poi -> !routeIds.contains(poi.id))
                .filter(poi -> !sameAsRoutePoint(poi, routePois, projection))
                .map(poi -> candidateScore(poi, routePois, projection))
                .filter(score -> score.distanceFromRouteMeters <= MAX_ROUTE_DISTANCE_METERS)
                .sorted(Comparator
                        .comparingDouble((ShelterCandidate score) -> score.distanceFromStartMeters)
                        .thenComparingDouble(score -> score.distanceFromRouteMeters)
                        .thenComparingInt(score -> score.poi.mapRank == null ? 999 : score.poi.mapRank)
                        .thenComparing(score -> safeName(score.poi).toLowerCase(Locale.ROOT)))
                .limit(MAX_SHELTER_CANDIDATES)
                .toList();
        return new ShelterSuggestions(routePois, candidates);
    }

    private ShelterCandidate candidateScore(PoiEntity poi, List<PoiEntity> routePois, RouteProjection projection) {
        Point point = projection.toPoint(poi);
        SegmentScore best = null;
        for (int index = 0; index < routePois.size() - 1; index++) {
            Point from = projection.routePoints.get(index);
            Point to = projection.routePoints.get(index + 1);
            SegmentScore score = distanceToSegment(point, from, to, projection.cumulativeMeters.get(index), index);
            if (best == null
                    || score.distanceFromRouteMeters < best.distanceFromRouteMeters
                    || (score.distanceFromRouteMeters == best.distanceFromRouteMeters
                    && score.distanceFromStartMeters < best.distanceFromStartMeters)) {
                best = score;
            }
        }
        if (best == null) {
            best = new SegmentScore(Double.MAX_VALUE, Double.MAX_VALUE, 0, routePois.get(0).id);
        }
        return new ShelterCandidate(poi, best.distanceFromRouteMeters, best.distanceFromStartMeters,
                best.segmentIndex, best.insertAfterPoiId);
    }

    private boolean sameAsRoutePoint(PoiEntity poi, List<PoiEntity> routePois, RouteProjection projection) {
        Point point = projection.toPoint(poi);
        return routePois.stream()
                .map(projection::toPoint)
                .anyMatch(routePoint -> distance(point, routePoint) < SAME_POINT_DISTANCE_METERS);
    }

    private SegmentScore distanceToSegment(Point point, Point from, Point to, double segmentStartMeters, int segmentIndex) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double segmentLengthSquared = dx * dx + dy * dy;
        if (segmentLengthSquared == 0.0) {
            return new SegmentScore(distance(point, from), segmentStartMeters, segmentIndex, from.poiId);
        }
        double rawT = ((point.x - from.x) * dx + (point.y - from.y) * dy) / segmentLengthSquared;
        double t = Math.max(0.0, Math.min(1.0, rawT));
        double closestX = from.x + t * dx;
        double closestY = from.y + t * dy;
        double distanceFromRoute = Math.hypot(point.x - closestX, point.y - closestY);
        double distanceFromStart = segmentStartMeters + Math.sqrt(segmentLengthSquared) * t;
        return new SegmentScore(distanceFromRoute, distanceFromStart, segmentIndex, from.poiId);
    }

    private void applyShelterSuggestions(
            AiChatResponse response,
            AiChatResponse.MapAction routeAction,
            Map<String, Object> payload,
            ShelterSuggestions suggestions,
            String weatherReason,
            boolean english
    ) {
        payload.put("weatherShelterSuggested", true);
        payload.put("weatherAdjusted", false);
        payload.put("weatherReason", weatherReason);
        payload.put("shelterWaypointIds", List.of());
        payload.put("originalPoiIds", List.copyOf(routeAction.poiIds));
        payload.put("shelterCandidateIds", suggestions.candidates.stream().map(score -> score.poi.id).toList());
        payload.put("shelterCandidates", suggestions.candidates.stream().map(this::candidatePayload).toList());

        List<PoiEntity> mergedPois = new ArrayList<>(suggestions.routePois);
        for (ShelterCandidate candidate : suggestions.candidates) {
            addUnique(mergedPois, candidate.poi);
        }
        for (PoiEntity poi : response.pois == null ? List.<PoiEntity>of() : response.pois) {
            addUnique(mergedPois, poi);
        }
        response.pois = mergedPois;
        response.reply = appendWeatherSuggestionReply(response.reply, weatherReason, english);
    }

    private Map<String, Object> candidatePayload(ShelterCandidate candidate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("poiId", candidate.poi.id);
        payload.put("name", candidate.poi.name);
        payload.put("distanceFromRouteMeters", Math.round(candidate.distanceFromRouteMeters));
        payload.put("distanceFromStartMeters", Math.round(candidate.distanceFromStartMeters));
        payload.put("segmentIndex", candidate.segmentIndex);
        payload.put("insertAfterPoiId", candidate.insertAfterPoiId);
        return payload;
    }

    private String appendWeatherSuggestionReply(String reply, String weatherReason, boolean english) {
        String suffix = english
                ? " Severe weather is active, so I found nearby sheltered candidates you can add to the route."
                : " \u68c0\u6d4b\u5230\u6076\u52a3\u5929\u6c14\uff0c\u5df2\u63a8\u8350\u9644\u8fd1\u53ef\u906e\u853d\u70b9\uff0c\u53ef\u9009\u62e9\u6700\u591a 2 \u4e2a\u52a0\u5165\u8def\u7ebf\u3002";
        if (!StringUtils.hasText(reply)) {
            return weatherReason + suffix;
        }
        if (reply.contains(suffix.trim())) {
            return reply;
        }
        return reply + suffix;
    }

    private String severeWeatherReason(WeatherResponse weather, boolean english) {
        if (weather == null || weather.now == null) {
            return "";
        }
        if (wetText(weather.now.text) || value(weather.now.precip) > 0.0) {
            return english ? "Rain or snow is affecting campus travel." : "\u6821\u56ed\u5f53\u524d\u5b58\u5728\u964d\u96e8\u6216\u964d\u96ea\u3002";
        }
        boolean wetNextHours = weather.hourly.stream().limit(6).anyMatch(item ->
                wetText(item.text) || value(item.precip) > 0.0 || value(item.pop) >= 50);
        if (wetNextHours) {
            return english ? "Rain is likely in the next few hours." : "\u672a\u6765\u51e0\u5c0f\u65f6\u964d\u6c34\u6982\u7387\u8f83\u9ad8\u3002";
        }
        if (maxWindScale(weather) >= 5) {
            return english ? "Wind is strong enough to affect open walking routes." : "\u98ce\u529b\u8f83\u5927\uff0c\u5f00\u9614\u8def\u6bb5\u901a\u884c\u4f53\u9a8c\u8f83\u5dee\u3002";
        }
        if (maxTemp(weather) >= 32 || value(weather.now.temp) >= 30) {
            return english ? "High temperature makes sheltered stops useful." : "\u6c14\u6e29\u8f83\u9ad8\uff0c\u9002\u5408\u589e\u52a0\u906e\u853d\u505c\u7559\u70b9\u3002";
        }
        if (minTemp(weather) <= 5 || value(weather.now.temp) <= 5) {
            return english ? "Low temperature makes sheltered stops useful." : "\u6c14\u6e29\u8f83\u4f4e\uff0c\u9002\u5408\u51cf\u5c11\u5f00\u9614\u8def\u6bb5\u505c\u7559\u3002";
        }
        return "";
    }

    private boolean isSheltered(PoiEntity poi) {
        String tags = poi.tags == null ? "" : poi.tags.toLowerCase(Locale.ROOT);
        return Boolean.TRUE.equals(poi.sheltered) || tags.contains("sheltered") || tags.contains("rain-friendly");
    }

    private boolean wetText(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return normalized.contains("\u96e8") || normalized.contains("\u96ea") || normalized.contains("rain")
                || normalized.contains("snow") || normalized.contains("shower") || normalized.contains("thunder");
    }

    private int maxTemp(WeatherResponse weather) {
        return weather.daily.stream().map(item -> value(item.tempMax)).max(Integer::compareTo).orElse(value(weather.now.temp));
    }

    private int minTemp(WeatherResponse weather) {
        return weather.daily.stream().map(item -> value(item.tempMin)).min(Integer::compareTo).orElse(value(weather.now.temp));
    }

    private int maxWindScale(WeatherResponse weather) {
        int nowWind = windScale(weather.now.windScale);
        int hourlyWind = weather.hourly.stream().map(item -> windScale(item.windScale)).max(Integer::compareTo).orElse(0);
        int dailyWind = weather.daily.stream().map(item -> windScale(item.windScaleDay)).max(Integer::compareTo).orElse(0);
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

    private boolean hasCoordinate(PoiEntity poi) {
        return poi != null && poi.id != null && poi.longitude != null && poi.latitude != null;
    }

    private double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    private double number(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private double value(Double value) {
        return value == null ? 0.0 : value;
    }

    private String safeName(PoiEntity poi) {
        return poi.name == null ? "" : poi.name;
    }

    private void addUnique(List<PoiEntity> pois, PoiEntity poi) {
        if (poi != null && poi.id != null && pois.stream().noneMatch(item -> item.id.equals(poi.id))) {
            pois.add(poi);
        }
    }

    private record ShelterSuggestions(List<PoiEntity> routePois, List<ShelterCandidate> candidates) {
    }

    private record ShelterCandidate(
            PoiEntity poi,
            double distanceFromRouteMeters,
            double distanceFromStartMeters,
            int segmentIndex,
            Long insertAfterPoiId
    ) {
    }

    private record SegmentScore(
            double distanceFromRouteMeters,
            double distanceFromStartMeters,
            int segmentIndex,
            Long insertAfterPoiId
    ) {
    }

    private record Point(Long poiId, double x, double y) {
    }

    private static class RouteProjection {
        private final double originLongitude;
        private final double originLatitude;
        private final double metersPerLongitudeDegree;
        private final List<Point> routePoints;
        private final List<Double> cumulativeMeters;

        private RouteProjection(
                double originLongitude,
                double originLatitude,
                double metersPerLongitudeDegree,
                List<Point> routePoints,
                List<Double> cumulativeMeters
        ) {
            this.originLongitude = originLongitude;
            this.originLatitude = originLatitude;
            this.metersPerLongitudeDegree = metersPerLongitudeDegree;
            this.routePoints = routePoints;
            this.cumulativeMeters = cumulativeMeters;
        }

        static RouteProjection from(List<PoiEntity> routePois) {
            double originLongitude = routePois.get(0).longitude.doubleValue();
            double originLatitude = routePois.get(0).latitude.doubleValue();
            double averageLatitude = routePois.stream().mapToDouble(poi -> poi.latitude.doubleValue()).average().orElse(originLatitude);
            double metersPerLongitudeDegree = Math.cos(Math.toRadians(averageLatitude)) * METERS_PER_LATITUDE_DEGREE;
            List<Point> points = routePois.stream()
                    .map(poi -> pointFor(poi, originLongitude, originLatitude, metersPerLongitudeDegree))
                    .toList();
            List<Double> cumulative = new ArrayList<>();
            double total = 0.0;
            cumulative.add(total);
            for (int index = 0; index < points.size() - 1; index++) {
                total += Math.hypot(points.get(index + 1).x - points.get(index).x, points.get(index + 1).y - points.get(index).y);
                cumulative.add(total);
            }
            return new RouteProjection(originLongitude, originLatitude, metersPerLongitudeDegree, points, cumulative);
        }

        Point toPoint(PoiEntity poi) {
            return pointFor(poi, originLongitude, originLatitude, metersPerLongitudeDegree);
        }

        private static Point pointFor(PoiEntity poi, double originLongitude, double originLatitude, double metersPerLongitudeDegree) {
            double x = (poi.longitude.doubleValue() - originLongitude) * metersPerLongitudeDegree;
            double y = (poi.latitude.doubleValue() - originLatitude) * METERS_PER_LATITUDE_DEGREE;
            return new Point(poi.id, x, y);
        }
    }
}
