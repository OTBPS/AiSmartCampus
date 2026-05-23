package com.smartcampus.navigation.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiService;
import com.smartcampus.navigation.weather.WeatherResponse;
import com.smartcampus.navigation.weather.WeatherService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteWeatherEnhancerTest {
    @Test
    void forcedWeatherTestingSuggestsCandidatesEvenWhenClear() {
        PoiEntity origin = poi(1L, "Dorm", 118.700, 32.200, false, 1);
        PoiEntity destination = poi(2L, "Library", 118.710, 32.200, false, 2);
        PoiEntity shelter = poi(3L, "Sheltered Corridor", 118.705, 32.200, true, 3);
        RouteWeatherEnhancer enhancer = enhancer(clearWeather(), List.of(origin, destination, shelter));
        AiChatResponse response = routeResponse(origin, destination);

        enhancer.enhance(response, "zh-CN");

        AiChatResponse.MapAction route = response.mapActions.get(0);
        assertEquals(List.of(1L, 2L), route.poiIds);
        assertTrue((Boolean) route.payload.get("weatherShelterSuggested"));
        assertFalse((Boolean) route.payload.get("weatherAdjusted"));
        assertEquals(List.of(3L), route.payload.get("shelterCandidateIds"));
    }

    @Test
    void rainyWeatherReturnsCandidatesWithoutChangingRoute() {
        PoiEntity origin = poi(1L, "Dorm", 118.700, 32.200, false, 1);
        PoiEntity destination = poi(2L, "Library", 118.710, 32.200, false, 2);
        PoiEntity shelter = poi(3L, "Sheltered Corridor", 118.705, 32.200, true, 3);
        RouteWeatherEnhancer enhancer = enhancer(rainyWeather(), List.of(origin, destination, shelter));
        AiChatResponse response = routeResponse(origin, destination);

        enhancer.enhance(response, "zh-CN");

        AiChatResponse.MapAction route = response.mapActions.get(0);
        assertEquals(List.of(1L, 2L), route.poiIds);
        assertTrue((Boolean) route.payload.get("weatherShelterSuggested"));
        assertFalse((Boolean) route.payload.get("weatherAdjusted"));
        assertEquals(List.of(3L), route.payload.get("shelterCandidateIds"));
        assertEquals(List.of(), route.payload.get("shelterWaypointIds"));
        assertEquals(List.of(1L, 2L), route.payload.get("originalPoiIds"));
        assertTrue(response.reply.contains("recommended nearby sheltered candidates")
                || response.reply.contains("\u5df2\u63a8\u8350\u9644\u8fd1\u53ef\u906e\u853d\u70b9"));
        assertEquals(List.of(1L, 2L), response.mapActions.get(1).poiIds);
        assertEquals(List.of(origin, destination, shelter), response.pois.subList(0, 3));

        List<?> candidates = (List<?>) route.payload.get("shelterCandidates");
        assertEquals(1, candidates.size());
        Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
        assertEquals(3L, candidate.get("poiId"));
        assertEquals(0L, candidate.get("distanceFromRouteMeters"));
        assertEquals(0, candidate.get("segmentIndex"));
        assertEquals(1L, candidate.get("insertAfterPoiId"));
    }

    @Test
    void filtersCandidatesMoreThanFiftyMetersFromPolyline() {
        PoiEntity origin = poi(1L, "Dorm", 118.700, 32.200, false, 1);
        PoiEntity destination = poi(2L, "Library", 118.710, 32.200, false, 2);
        PoiEntity near = poi(3L, "Near Shelter", 118.705, 32.2004, true, 3);
        PoiEntity far = poi(4L, "Far Shelter", 118.705, 32.2007, true, 4);
        RouteWeatherEnhancer enhancer = enhancer(rainyWeather(), List.of(origin, destination, near, far));
        AiChatResponse response = routeResponse(origin, destination);

        enhancer.enhance(response, "en-US");

        AiChatResponse.MapAction route = response.mapActions.get(0);
        assertEquals(List.of(3L), route.payload.get("shelterCandidateIds"));
    }

    @Test
    void excludesCandidatesAlmostSameAsRoutePoints() {
        PoiEntity origin = poi(1L, "Dorm", 118.700, 32.200, false, 1);
        PoiEntity waypoint = poi(2L, "Teaching", 118.705, 32.200, false, 2);
        PoiEntity destination = poi(3L, "Library", 118.710, 32.200, false, 3);
        PoiEntity duplicateOrigin = poi(4L, "Duplicate Origin Shelter", 118.70002, 32.200, true, 4);
        PoiEntity duplicateWaypoint = poi(5L, "Duplicate Waypoint Shelter", 118.70502, 32.200, true, 5);
        PoiEntity duplicateDestination = poi(6L, "Duplicate Destination Shelter", 118.71002, 32.200, true, 6);
        PoiEntity valid = poi(7L, "Valid Shelter", 118.707, 32.200, true, 7);
        RouteWeatherEnhancer enhancer = enhancer(rainyWeather(), List.of(
                origin, waypoint, destination, duplicateOrigin, duplicateWaypoint, duplicateDestination, valid));
        AiChatResponse response = routeResponse(origin, waypoint, destination);

        enhancer.enhance(response, "en-US");

        AiChatResponse.MapAction route = response.mapActions.get(0);
        assertEquals(List.of(7L), route.payload.get("shelterCandidateIds"));
    }

    @Test
    void sortsByStartDistanceRouteDistancePriorityAndName() {
        PoiEntity origin = poi(1L, "Dorm", 118.700, 32.200, false, 1);
        PoiEntity destination = poi(2L, "Library", 118.710, 32.200, false, 2);
        PoiEntity laterOnRoute = poi(3L, "Later On Route", 118.704, 32.200, true, 1);
        PoiEntity earlyFarther = poi(4L, "Early Farther", 118.702, 32.2002, true, 9);
        PoiEntity beta = poi(5L, "Beta Shelter", 118.706, 32.200, true, 5);
        PoiEntity alpha = poi(6L, "Alpha Shelter", 118.706, 32.200, true, 5);
        PoiEntity higherPriority = poi(7L, "Priority Shelter", 118.706, 32.200, true, 1);
        RouteWeatherEnhancer enhancer = enhancer(rainyWeather(), List.of(
                origin, destination, laterOnRoute, earlyFarther, beta, alpha, higherPriority));
        AiChatResponse response = routeResponse(origin, destination);

        enhancer.enhance(response, "en-US");

        AiChatResponse.MapAction route = response.mapActions.get(0);
        assertEquals(List.of(4L, 3L, 7L, 6L, 5L), route.payload.get("shelterCandidateIds"));
    }

    @Test
    void returnsAtMostFiveCandidates() {
        PoiEntity origin = poi(1L, "Dorm", 118.700, 32.200, false, 1);
        PoiEntity destination = poi(2L, "Library", 118.710, 32.200, false, 2);
        RouteWeatherEnhancer enhancer = enhancer(rainyWeather(), List.of(
                origin, destination,
                poi(3L, "Shelter 1", 118.701, 32.200, true, 1),
                poi(4L, "Shelter 2", 118.702, 32.200, true, 2),
                poi(5L, "Shelter 3", 118.703, 32.200, true, 3),
                poi(6L, "Shelter 4", 118.704, 32.200, true, 4),
                poi(7L, "Shelter 5", 118.705, 32.200, true, 5),
                poi(8L, "Shelter 6", 118.706, 32.200, true, 6)
        ));
        AiChatResponse response = routeResponse(origin, destination);

        enhancer.enhance(response, "en-US");

        AiChatResponse.MapAction route = response.mapActions.get(0);
        assertEquals(List.of(3L, 4L, 5L, 6L, 7L), route.payload.get("shelterCandidateIds"));
        List<?> candidates = (List<?>) route.payload.get("shelterCandidates");
        assertEquals(5, candidates.size());
    }

    @Test
    void noShelterCandidateKeepsRouteUnchanged() {
        PoiEntity origin = poi(1L, "Dorm", 118.700, 32.200, false, 1);
        PoiEntity destination = poi(2L, "Library", 118.710, 32.200, false, 2);
        RouteWeatherEnhancer enhancer = enhancer(rainyWeather(), List.of(origin, destination));
        AiChatResponse response = routeResponse(origin, destination);

        enhancer.enhance(response, "zh-CN");

        AiChatResponse.MapAction route = response.mapActions.get(0);
        assertEquals(List.of(1L, 2L), route.poiIds);
        assertFalse((Boolean) route.payload.get("weatherAdjusted"));
        assertFalse(route.payload.containsKey("shelterCandidateIds"));
    }

    @Test
    void weatherFailureKeepsRouteUnchanged() {
        PoiEntity origin = poi(1L, "Dorm", 118.700, 32.200, false, 1);
        PoiEntity destination = poi(2L, "Library", 118.710, 32.200, false, 2);
        WeatherService weatherService = mock(WeatherService.class);
        PoiService poiService = mock(PoiService.class);
        when(weatherService.campus(eq(null), eq("zh-CN"))).thenThrow(new IllegalStateException("weather down"));
        RouteWeatherEnhancer enhancer = new RouteWeatherEnhancer(weatherService, poiService);
        AiChatResponse response = routeResponse(origin, destination);

        enhancer.enhance(response, "zh-CN");

        AiChatResponse.MapAction route = response.mapActions.get(0);
        assertEquals(List.of(1L, 2L), route.poiIds);
        assertFalse((Boolean) route.payload.get("weatherAdjusted"));
    }

    private RouteWeatherEnhancer enhancer(WeatherResponse weather, List<PoiEntity> pois) {
        WeatherService weatherService = mock(WeatherService.class);
        PoiService poiService = mock(PoiService.class);
        when(weatherService.campus(eq(null), eq("zh-CN"))).thenReturn(weather);
        when(weatherService.campus(eq(null), eq("en-US"))).thenReturn(weather);
        when(poiService.list(eq(null), eq(null), eq(null), eq(true))).thenReturn(pois);
        return new RouteWeatherEnhancer(weatherService, poiService);
    }

    private AiChatResponse routeResponse(PoiEntity... routePois) {
        assertNotNull(routePois);
        AiChatResponse response = new AiChatResponse();
        response.intent = "route_help";
        response.reply = "Route prepared.";
        response.pois = List.of(routePois);
        AiChatResponse.MapAction route = new AiChatResponse.MapAction("draw_route");
        route.poiIds = List.of(routePois).stream().map(poi -> poi.id).toList();
        route.routeMode = "AMAP_FALLBACK";
        route.payload = new LinkedHashMap<>(Map.of("source", "map_context"));
        response.mapActions.add(route);
        AiChatResponse.MapAction highlight = new AiChatResponse.MapAction("highlight_pois");
        highlight.poiIds = List.of(routePois).stream().map(poi -> poi.id).toList();
        response.mapActions.add(highlight);
        return response;
    }

    private WeatherResponse clearWeather() {
        WeatherResponse weather = new WeatherResponse();
        weather.now = new WeatherResponse.CurrentWeather();
        weather.now.text = "Sunny";
        weather.now.temp = 22;
        weather.now.windScale = "2";
        return weather;
    }

    private WeatherResponse rainyWeather() {
        WeatherResponse weather = new WeatherResponse();
        weather.now = new WeatherResponse.CurrentWeather();
        weather.now.text = "Rain";
        weather.now.temp = 18;
        weather.now.windScale = "2";
        return weather;
    }

    private PoiEntity poi(Long id, String name, double longitude, double latitude, boolean sheltered, Integer mapRank) {
        PoiEntity poi = new PoiEntity();
        poi.id = id;
        poi.name = name;
        poi.category = "STUDY";
        poi.longitude = BigDecimal.valueOf(longitude);
        poi.latitude = BigDecimal.valueOf(latitude);
        poi.locationText = "Campus";
        poi.tags = sheltered ? "sheltered,rain-friendly" : "";
        poi.sheltered = sheltered;
        poi.enabled = true;
        poi.mapRank = mapRank;
        return poi;
    }
}
