package com.smartcampus.navigation.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcampus.navigation.common.BizException;
import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MockAiService {
    private final PoiService poiService;
    private final AiMessageMapper aiMessageMapper;
    private final ObjectMapper objectMapper;

    public MockAiService(PoiService poiService, AiMessageMapper aiMessageMapper, ObjectMapper objectMapper) {
        this.poiService = poiService;
        this.aiMessageMapper = aiMessageMapper;
        this.objectMapper = objectMapper;
    }

    public AiChatResponse chat(Long userId, String message, String locale) {
        return chat(userId, message, locale, null);
    }

    public AiChatResponse chat(Long userId, String message, String locale, AiChatRequest.RouteContext routeContext) {
        AiChatResponse response = buildResponse(message, locale, routeContext);
        try {
            AiMessageEntity log = new AiMessageEntity();
            log.userId = userId;
            log.question = message;
            log.intent = response.intent;
            log.reply = response.reply;
            log.toolCallsJson = objectMapper.writeValueAsString(response.toolCalls);
            log.mapActionsJson = objectMapper.writeValueAsString(response.mapActions);
            aiMessageMapper.insert(log);
        } catch (Exception ex) {
            throw new BizException("AI 交互记录保存失败");
        }
        return response;
    }

    public AiChatResponse preview(String message) {
        return buildResponse(message, "zh-CN", null);
    }

    public AiChatResponse preview(String message, String locale) {
        return buildResponse(message, locale, null);
    }

    public AiChatResponse preview(String message, String locale, AiChatRequest.RouteContext routeContext) {
        return buildResponse(message, locale, routeContext);
    }

    public List<AiMessageEntity> logs() {
        return aiMessageMapper.selectList(new QueryWrapper<AiMessageEntity>().orderByDesc("created_at").last("LIMIT 100"));
    }

    private AiChatResponse buildResponse(String message, String locale, AiChatRequest.RouteContext routeContext) {
        String normalized = normalizeText(message);
        if (isNoteQuestion(normalized)) {
            return findNote(locale);
        }
        if (isSmallTalk(normalized)) {
            return smallTalk(normalized, locale);
        }
        if (isCurrentLocationRouteQuestion(normalized)) {
            return routeFromCurrentLocation(message, locale, routeContext);
        }
        if (isNearbyRecommendationQuestion(normalized)) {
            return recommendNearbyPlaces(message, locale, routeContext);
        }
        if (mentionsCurrentLocationOrNearby(normalized) && !hasCurrentLocation(routeContext)) {
            return locationRequired(locale, "nearby".equals(currentLocationRequestKind(normalized)) ? "recommend_place" : "route_help");
        }
        if (isRankedPlaceQuestion(normalized)) {
            return recommendRankedPlaces(message, locale);
        }
        if (isRouteQuestion(normalized) || isPlainChineseRouteQuestion(normalized) || looksLikeRouteWithContext(message, routeContext)) {
            return routeHelp(message, locale, routeContext);
        }
        if (isRecommendationQuestion(normalized) || isPlainChineseRecommendationQuestion(normalized)) {
            return recommendPlace(message, locale);
        }
        return findPoi(message, locale);
    }

    private AiChatResponse findNote(String locale) {
        return base("find_note", text(locale,
                "\u6211\u4f1a\u4ece\u53d1\u73b0 note \u4e2d\u5bfb\u627e\u76f8\u5173\u5185\u5bb9\u3002",
                "I will search related discover notes."), List.of());
    }

    private AiChatResponse smallTalk(String normalized, String locale) {
        String reply;
        if (isCampusIntroQuestion(normalized)) {
            reply = text(locale,
                    "\u5357\u4eac\u4fe1\u606f\u5de5\u7a0b\u5927\u5b66\uff08NUIST\uff09\u4f4d\u4e8e\u5357\u4eac\uff0c\u662f\u4e00\u6240\u4ee5\u5927\u6c14\u79d1\u5b66\u4e3a\u7279\u8272\uff0c\u6c14\u8c61\u3001\u4fe1\u606f\u3001\u73af\u5883\u7b49\u591a\u5b66\u79d1\u534f\u540c\u53d1\u5c55\u7684\u9ad8\u6821\u3002\u5728\u8fd9\u4e2a\u7cfb\u7edf\u91cc\uff0c\u6211\u8fd8\u53ef\u4ee5\u7ee7\u7eed\u5e2e\u4f60\u67e5\u6821\u56ed\u5730\u70b9\u3001\u89c4\u5212\u8def\u7ebf\u3001\u770b\u5929\u6c14\u6216\u68c0\u7d22\u53d1\u73b0 note\u3002",
                    "Nanjing University of Information Science & Technology (NUIST) is based in Nanjing and is known for atmospheric science, with coordinated strengths across meteorology, information, environmental, and related disciplines. In this app, I can also help you find campus places, plan routes, check weather, or search discover notes.");
        } else if (containsAny(normalized, "thanks", "thankyou", "\u8c22\u8c22", "\u591a\u8c22")) {
            reply = text(locale, "\u4e0d\u5ba2\u6c14\u3002", "You're welcome.");
        } else if (containsAny(normalized, "help", "\u5e2e\u52a9", "\u600e\u4e48\u7528")) {
            reply = text(locale,
                    "\u6211\u53ef\u4ee5\u5e2e\u4f60\u67e5\u6821\u56ed\u5730\u70b9\u3001\u89c4\u5212\u8def\u7ebf\u3001\u63a8\u8350\u81ea\u4e60\u70b9\u6216\u7ed3\u5408\u5929\u6c14\u7ed9\u51fa\u5efa\u8bae\u3002",
                    "I can help with campus places, routes, weather, and study tips.");
        } else if (containsAny(normalized, "whoareyou", "\u4f60\u662f\u8c01")) {
            reply = text(locale,
                    "\u6211\u662f SmartCampus \u7684 AI \u5730\u56fe\u52a9\u624b\u3002",
                    "I am the SmartCampus AI map assistant.");
        } else {
            reply = text(locale,
                    "\u4f60\u597d\uff0c\u6211\u53ef\u4ee5\u5e2e\u4f60\u67e5\u5730\u70b9\u3001\u89c4\u5212\u8def\u7ebf\u6216\u63a8\u8350\u6821\u56ed\u573a\u6240\u3002",
                    "Hi, I can help with campus places, routes, weather, and study tips.");
        }
        return base("small_talk", reply, List.of());
    }

    private AiChatResponse findPoi(String message, String locale) {
        String keyword = extractKeyword(message);
        List<PoiEntity> all = poiService.list(null, null, null, true);
        if (all == null) {
            all = List.of();
        }
        List<PoiEntity> pois = matchPoisInMessage(message, all);
        if (pois.isEmpty()) {
            pois = poiService.list(keyword, null, null, true);
        }
        if (pois == null) {
            pois = List.of();
        }
        if (pois.isEmpty()) {
            AiChatResponse response = base("find_poi", text(locale,
                    "\u6682\u672a\u5bfb\u627e\u5230\u76f8\u5173\u5730\u70b9,\u8bf7\u5c1d\u8bd5\u522b\u7684\u5730\u70b9",
                    "No related campus place was found. Please try another place."), pois);
            response.toolCalls.add(new AiChatResponse.ToolCall("searchPoi", Map.of("keyword", keyword)));
            response.mapActions.add(highlight(pois));
            return response;
        }
        AiChatResponse response = base("find_poi", text(locale,
                "我根据你的问题找到了这些校园地点，并已在地图上高亮。",
                "I found these campus places from your question and highlighted them on the map."), pois);
        response.toolCalls.add(new AiChatResponse.ToolCall("searchPoi", Map.of("keyword", keyword)));
        response.mapActions.add(highlight(pois));
        if (!pois.isEmpty()) {
            response.mapActions.add(openDetail(pois.get(0).id));
        }
        return response;
    }

    private AiChatResponse recommendPlace(String message, String locale) {
        String normalized = normalizeText(message);
        List<String> tags = extractPreferenceTags(normalized);
        String tag = tags.isEmpty() ? "study" : tags.get(0);
        List<PoiEntity> pois = poiService.list(null, null, null, true).stream()
                .filter(poi -> matchesRecommendation(poi, tags, normalized))
                .sorted(Comparator.comparingInt((PoiEntity poi) -> scoreRecommendation(poi, tags, normalized)).reversed())
                .limit(4)
                .toList();
        if (pois.isEmpty()) {
            pois = poiService.list(null, null, tag, true);
        }
        if (pois.isEmpty()) {
            pois = poiService.list(null, "STUDY", null, true).stream().limit(4).toList();
        }
        AiChatResponse response = base("recommend_place", recommendationReply(locale, normalized), pois);
        response.toolCalls.add(new AiChatResponse.ToolCall("searchPoiByTags", Map.of("tags", tags)));
        response.mapActions.add(highlight(pois));
        if (!pois.isEmpty()) {
            response.mapActions.add(openDetail(pois.get(0).id));
        }
        return response;
    }

    private AiChatResponse recommendRankedPlaces(String message, String locale) {
        String normalized = normalizeText(message);
        int limit = requestedLimit(normalized);
        String category = rankedPlaceCategory(normalized);
        List<PoiEntity> pois = poiService.list(null, null, null, true).stream()
                .filter(poi -> matchesRankedPlaceCategory(poi, category, normalized))
                .sorted(Comparator.comparingInt((PoiEntity poi) -> poi.mapRank == null ? 999 : poi.mapRank)
                        .thenComparing(poi -> poi.name == null ? "" : poi.name))
                .limit(limit)
                .toList();

        AiChatResponse response = base("recommend_place", rankedPlaceReply(locale, pois.size(), limit, category), pois);
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("sort", "mapRank");
        arguments.put("limit", limit);
        if (category != null) {
            arguments.put("category", category);
        }
        response.toolCalls.add(new AiChatResponse.ToolCall("searchPoiByRank", arguments));
        response.mapActions.add(highlight(pois));
        if (!pois.isEmpty()) {
            response.mapActions.add(openDetail(pois.get(0).id));
        }
        return response;
    }

    private AiChatResponse routeFromCurrentLocation(String message, String locale, AiChatRequest.RouteContext routeContext) {
        if (!hasCurrentLocation(routeContext)) {
            return locationRequired(locale, "route_help");
        }
        List<PoiEntity> all = poiService.list(null, null, null, true);
        if (all == null) {
            all = List.of();
        }
        List<PoiEntity> matched = matchPoisInMessage(message, all);
        if (matched.isEmpty()) {
            AiChatResponse response = base("route_help", text(locale,
                    "\u8bf7\u8bf4\u660e\u8981\u524d\u5f80\u7684\u6821\u56ed\u5730\u70b9\uff0c\u4f8b\u5982\uff1a\u6211\u60f3\u4ece\u5f53\u524d\u4f4d\u7f6e\u524d\u5f80\u56fe\u4e66\u9986\u3002",
                    "Please name the campus destination, for example: go from my current location to the library."), List.of());
            response.toolCalls.add(new AiChatResponse.ToolCall("planCampusRouteFromCurrentLocation", Map.of("missing", "destination")));
            return response;
        }

        List<PoiEntity> routePois = matched.stream().limit(6).toList();
        PoiEntity destination = routePois.get(routePois.size() - 1);
        List<String> via = routePois.size() > 1
                ? routePois.subList(0, routePois.size() - 1).stream().map(poi -> poi.name).toList()
                : List.of();
        Map<String, Object> startPoint = currentLocationPayload(routeContext.currentLocation, locale);
        String from = startPoint.get("label").toString();
        String to = destination.name;

        AiChatResponse response = base("route_help", text(locale,
                "\u5df2\u751f\u6210\u4ece\u5f53\u524d\u4f4d\u7f6e\u5230 " + to + " \u7684\u6821\u56ed\u8def\u7ebf\u3002",
                "I prepared the campus route from your current location to " + to + "."), routePois);
        List<Long> ids = routePois.stream().map(poi -> poi.id).toList();
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("poiIds", ids);
        arguments.put("source", "current_location");
        arguments.put("startPoint", startPoint);
        response.toolCalls.add(new AiChatResponse.ToolCall("planCampusRouteFromCurrentLocation", arguments));

        AiChatResponse.MapAction action = new AiChatResponse.MapAction("draw_route");
        action.poiIds = ids;
        action.routeMode = "AMAP_FALLBACK";
        action.payload = new LinkedHashMap<>();
        action.payload.put("reason", text(locale, "\u5f53\u524d\u4f4d\u7f6e\u5230\u76ee\u6807\u5730\u70b9", "current location to destination"));
        action.payload.put("from", from);
        action.payload.put("to", to);
        action.payload.put("via", via);
        action.payload.put("source", "current_location");
        action.payload.put("startPoint", startPoint);
        response.mapActions.add(action);
        response.mapActions.add(highlight(routePois));
        response.mapActions.add(openDetail(destination.id));
        return response;
    }

    private AiChatResponse recommendNearbyPlaces(String message, String locale, AiChatRequest.RouteContext routeContext) {
        if (!hasCurrentLocation(routeContext)) {
            return locationRequired(locale, "recommend_place");
        }
        String normalized = normalizeText(message);
        int limit = requestedLimit(normalized);
        String category = rankedPlaceCategory(normalized);
        List<String> tags = extractPreferenceTags(normalized);
        List<PoiEntity> all = poiService.list(null, null, null, true);
        if (all == null) {
            all = List.of();
        }
        List<PoiEntity> matching = all.stream()
                .filter(this::hasPoiCoordinate)
                .filter(poi -> matchesNearbyTarget(poi, category, tags, normalized))
                .sorted(Comparator.comparingDouble(poi -> distanceMeters(routeContext.currentLocation, poi)))
                .limit(limit)
                .toList();
        if (matching.isEmpty()) {
            matching = all.stream()
                    .filter(this::hasPoiCoordinate)
                    .sorted(Comparator.comparingDouble(poi -> distanceMeters(routeContext.currentLocation, poi)))
                    .limit(limit)
                    .toList();
        }

        AiChatResponse response = base("recommend_place", nearbyReply(locale, matching.size(), category), matching);
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("sort", "distance");
        arguments.put("limit", limit);
        arguments.put("source", "current_location");
        arguments.put("currentLocation", currentLocationPayload(routeContext.currentLocation, locale));
        if (category != null) {
            arguments.put("category", category);
        }
        if (!tags.isEmpty()) {
            arguments.put("tags", tags);
        }
        response.toolCalls.add(new AiChatResponse.ToolCall("recommendNearbyPois", arguments));
        response.mapActions.add(highlight(matching));
        if (!matching.isEmpty()) {
            response.mapActions.add(openDetail(matching.get(0).id));
        }
        return response;
    }

    private AiChatResponse locationRequired(String locale, String intent) {
        AiChatResponse response = base(intent, text(locale,
                "\u8bf7\u5148\u5141\u8bb8\u6d4f\u89c8\u5668\u5b9a\u4f4d\uff0c\u6216\u70b9\u51fb\u5730\u56fe\u4e0a\u7684\u5b9a\u4f4d\u6309\u94ae\u540e\u518d\u8bd5\u3002",
                "Please allow browser location or click the map location button, then try again."), List.of());
        response.toolCalls.add(new AiChatResponse.ToolCall("requestCurrentLocation", Map.of("reason", intent)));
        return response;
    }

    private boolean matchesNearbyTarget(PoiEntity poi, String category, List<String> tags, String normalized) {
        if (category != null) {
            return matchesRankedPlaceCategory(poi, category, normalized);
        }
        if (!tags.isEmpty()) {
            return matchesRecommendation(poi, tags, normalized);
        }
        return true;
    }

    private String nearbyReply(String locale, int count, String category) {
        if ("en-US".equals(locale)) {
            return count == 0
                    ? "No nearby matching campus places were found."
                    : "I found the nearest " + count + " matching campus places from your current location.";
        }
        String target = switch (category == null ? "" : category) {
            case "DINING" -> "\u9910\u996e\u5730\u70b9";
            case "STUDY" -> "\u5b66\u4e60\u5730\u70b9";
            case "SPORTS" -> "\u8fd0\u52a8\u5730\u70b9";
            case "DORM" -> "\u5bbf\u820d";
            case "TEACHING" -> "\u6559\u5b66\u5730\u70b9";
            case "SERVICE" -> "\u670d\u52a1\u5730\u70b9";
            default -> "\u5730\u70b9";
        };
        return count == 0
                ? "\u6682\u672a\u627e\u5230\u9644\u8fd1\u5339\u914d\u7684" + target + "\u3002"
                : "\u5df2\u6309\u4f60\u7684\u5f53\u524d\u4f4d\u7f6e\u8ddd\u79bb\uff0c\u63a8\u8350\u6700\u8fd1\u7684 " + count + " \u4e2a" + target + "\u3002";
    }

    private boolean hasCurrentLocation(AiChatRequest.RouteContext routeContext) {
        return routeContext != null
                && routeContext.currentLocation != null
                && validCoordinate(routeContext.currentLocation.longitude, routeContext.currentLocation.latitude);
    }

    private Map<String, Object> currentLocationPayload(AiChatRequest.CurrentLocation location, String locale) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("longitude", location.longitude);
        point.put("latitude", location.latitude);
        if (location.accuracyMeters != null) {
            point.put("accuracyMeters", location.accuracyMeters);
        }
        point.put("label", location.label == null || location.label.isBlank()
                ? text(locale, "\u5f53\u524d\u4f4d\u7f6e", "Current location")
                : location.label);
        point.put("coordinateSystem", location.coordinateSystem == null || location.coordinateSystem.isBlank()
                ? "GCJ02"
                : location.coordinateSystem);
        return point;
    }

    private double distanceMeters(AiChatRequest.CurrentLocation location, PoiEntity poi) {
        double lng1 = location.longitude;
        double lat1 = location.latitude;
        double lng2 = poi.longitude.doubleValue();
        double lat2 = poi.latitude.doubleValue();
        double earthRadiusMeters = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }

    private boolean hasPoiCoordinate(PoiEntity poi) {
        return poi != null
                && poi.longitude != null
                && poi.latitude != null
                && validCoordinate(poi.longitude.doubleValue(), poi.latitude.doubleValue());
    }

    private boolean validCoordinate(Double longitude, Double latitude) {
        return longitude != null
                && latitude != null
                && Double.isFinite(longitude)
                && Double.isFinite(latitude)
                && Math.abs(longitude) <= 180
                && Math.abs(latitude) <= 90;
    }

    private AiChatResponse routeHelp(String message, String locale, AiChatRequest.RouteContext routeContext) {
        List<PoiEntity> all = poiService.list(null, null, null, true);
        Map<Long, PoiEntity> byId = new LinkedHashMap<>();
        for (PoiEntity poi : all) {
            byId.put(poi.id, poi);
        }

        List<PoiEntity> textMatched = matchPoisInMessage(message, all);
        PoiEntity contextOrigin = poiById(routeContext == null ? null : routeContext.originPoiId, byId);
        PoiEntity contextDestination = poiById(routeContext == null ? null : routeContext.destinationPoiId, byId);
        List<PoiEntity> contextWaypoints = poisByIds(routeContext == null ? null : routeContext.waypointPoiIds, byId);

        PoiEntity origin = null;
        PoiEntity destination = null;
        List<PoiEntity> textWaypoints = new ArrayList<>();
        boolean textIsDestinationOnly = isDestinationOnlyRouteText(message);

        if (textMatched.size() >= 2) {
            origin = textMatched.get(0);
            int waypointMarkerIndex = routeWaypointMarkerIndex(message);
            if (waypointMarkerIndex >= 0) {
                PoiEntity routeOrigin = origin;
                List<PoiEntity> beforeWaypoints = textMatched.stream()
                        .filter(poi -> routeMentionIndex(poi, normalizeText(message)) < waypointMarkerIndex)
                        .filter(poi -> !samePoi(poi, routeOrigin))
                        .toList();
                List<PoiEntity> afterWaypointMarker = textMatched.stream()
                        .filter(poi -> routeMentionIndex(poi, normalizeText(message)) >= waypointMarkerIndex)
                        .toList();
                destination = beforeWaypoints.isEmpty() ? textMatched.get(textMatched.size() - 1) : beforeWaypoints.get(beforeWaypoints.size() - 1);
                textWaypoints.addAll(afterWaypointMarker);
            } else {
                destination = textMatched.get(textMatched.size() - 1);
                textWaypoints.addAll(textMatched.subList(1, textMatched.size() - 1));
            }
        } else if (textMatched.size() == 1) {
            PoiEntity textPoi = textMatched.get(0);
            if (contextOrigin != null && (textIsDestinationOnly || contextDestination == null)) {
                origin = contextOrigin;
                destination = textPoi;
            } else if (contextDestination != null) {
                origin = textPoi;
                destination = contextDestination;
            } else {
                origin = textPoi;
            }
        }

        if (origin == null) {
            origin = contextOrigin;
        }
        if (destination == null) {
            destination = contextDestination;
        }

        List<PoiEntity> routePois = new ArrayList<>();
        addUnique(routePois, origin);
        for (PoiEntity waypoint : contextWaypoints) {
            if (!samePoi(waypoint, origin) && !samePoi(waypoint, destination)) {
                addUnique(routePois, waypoint);
            }
        }
        for (PoiEntity waypoint : textWaypoints) {
            if (!samePoi(waypoint, origin) && !samePoi(waypoint, destination)) {
                addUnique(routePois, waypoint);
            }
        }
        addUnique(routePois, destination);

        boolean usedContext = contextOrigin != null || contextDestination != null || !contextWaypoints.isEmpty();
        boolean usedText = !textMatched.isEmpty();
        String source = usedText && usedContext ? "mixed" : usedContext ? "map_context" : "text";

        if (routePois.size() < 2) {
            routePois = all.stream().limit(2).toList();
            source = "fallback";
        }

        String from = routePois.isEmpty() ? "start" : routePois.get(0).name;
        String to = routePois.size() < 2 ? "destination" : routePois.get(routePois.size() - 1).name;
        List<String> via = routePois.size() > 2
                ? routePois.subList(1, routePois.size() - 1).stream().map(poi -> poi.name).toList()
                : List.of();
        AiChatResponse response = base("route_help", text(locale,
                "\u5df2\u751f\u6210\u4ece " + from + " \u5230 " + to + " \u7684\u6821\u56ed\u8def\u7ebf\uff0c\u5e76\u6309\u987a\u5e8f\u4fdd\u7559\u9014\u7ecf\u70b9\u3002",
                "I prepared the campus route from " + from + " to " + to + " and kept the waypoints in order."), routePois);
        List<Long> ids = routePois.stream().map(poi -> poi.id).toList();
        response.toolCalls.add(new AiChatResponse.ToolCall("planCampusRouteFallback", Map.of("poiIds", ids, "source", source)));
        AiChatResponse.MapAction action = new AiChatResponse.MapAction("draw_route");
        action.poiIds = ids;
        action.routeMode = "AMAP_FALLBACK";
        action.payload = new LinkedHashMap<>();
        action.payload.put("reason", text(locale, "\u666e\u901a\u6b65\u884c\u8def\u7ebf\u515c\u5e95", "standard walking route fallback"));
        action.payload.put("from", from);
        action.payload.put("to", to);
        action.payload.put("via", via);
        action.payload.put("source", source);
        response.mapActions.add(action);
        response.mapActions.add(highlight(routePois));
        if (routePois.size() > 1) {
            response.mapActions.add(openDetail(routePois.get(routePois.size() - 1).id));
        }
        return response;
    }

    private AiChatResponse routeHelp(String message, String locale) {
        List<PoiEntity> all = poiService.list(null, null, null, true);
        List<PoiEntity> matched = matchPoisInMessage(message, all);
        if (matched.size() < 2) {
            matched = all.stream().limit(2).toList();
        }
        String from = matched.isEmpty() ? text(locale, "起点", "start") : matched.get(0).name;
        String to = matched.size() < 2 ? text(locale, "终点", "destination") : matched.get(1).name;
        AiChatResponse response = base("route_help", text(locale,
                "我已识别从 " + from + " 到 " + to + " 的路线意图，并准备触发普通路线兜底。第一版不做复杂路线优化，但会保留地点上下文。",
                "I recognized a route request from " + from + " to " + to + " and prepared the standard route fallback. The first version keeps place context without complex route optimization."), matched);
        List<Long> ids = matched.stream().map(poi -> poi.id).toList();
        response.toolCalls.add(new AiChatResponse.ToolCall("planCampusRouteFallback", Map.of("poiIds", ids)));
        AiChatResponse.MapAction action = new AiChatResponse.MapAction("draw_route");
        action.poiIds = ids;
        action.routeMode = "AMAP_FALLBACK";
        action.payload = new LinkedHashMap<>();
        action.payload.put("reason", text(locale, "普通路线兜底", "standard route fallback"));
        action.payload.put("from", from);
        action.payload.put("to", to);
        response.mapActions.add(action);
        response.mapActions.add(highlight(matched));
        if (matched.size() > 1) {
            response.mapActions.add(openDetail(matched.get(1).id));
        }
        return response;
    }

    private AiChatResponse base(String intent, String reply, List<PoiEntity> pois) {
        AiChatResponse response = new AiChatResponse();
        response.intent = intent;
        response.reply = reply;
        response.pois = pois;
        return response;
    }

    private AiChatResponse.MapAction highlight(List<PoiEntity> pois) {
        AiChatResponse.MapAction action = new AiChatResponse.MapAction("highlight_pois");
        action.poiIds = pois.stream().map(poi -> poi.id).toList();
        return action;
    }

    private AiChatResponse.MapAction openDetail(Long poiId) {
        AiChatResponse.MapAction action = new AiChatResponse.MapAction("open_poi_detail");
        action.poiId = poiId;
        return action;
    }

    private PoiEntity poiById(Long id, Map<Long, PoiEntity> byId) {
        return id == null ? null : byId.get(id);
    }

    private List<PoiEntity> poisByIds(List<Long> ids, Map<Long, PoiEntity> byId) {
        List<PoiEntity> pois = new ArrayList<>();
        if (ids == null) {
            return pois;
        }
        for (Long id : ids) {
            addUnique(pois, poiById(id, byId));
        }
        return pois;
    }

    private void addUnique(List<PoiEntity> pois, PoiEntity poi) {
        if (poi == null || pois.stream().anyMatch(item -> samePoi(item, poi))) {
            return;
        }
        pois.add(poi);
    }

    private boolean samePoi(PoiEntity left, PoiEntity right) {
        return left != null && right != null && left.id != null && left.id.equals(right.id);
    }

    private boolean isDestinationOnlyRouteText(String message) {
        String normalized = normalizeText(message);
        boolean hasOriginWord = containsAny(normalized, "from", "start", "\u4ece", "\u51fa\u53d1");
        boolean hasDestinationWord = containsAny(normalized, "to", "go", "destination", "\u5230", "\u53bb", "\u524d\u5f80", "\u5f80");
        return hasDestinationWord && !hasOriginWord;
    }

    private int routeWaypointMarkerIndex(String message) {
        String normalized = normalizeText(message);
        int best = Integer.MAX_VALUE;
        for (String marker : List.of("via", "passby", "through", "\u9014\u7ecf", "\u7ecf\u8fc7")) {
            int index = normalized.indexOf(normalizeText(marker));
            if (index >= 0 && index < best) {
                best = index;
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    private boolean looksLikeRouteWithContext(String message, AiChatRequest.RouteContext routeContext) {
        if (routeContext == null) {
            return false;
        }
        boolean hasContext = routeContext.originPoiId != null
                || routeContext.destinationPoiId != null
                || (routeContext.waypointPoiIds != null && !routeContext.waypointPoiIds.isEmpty());
        if (!hasContext) {
            return false;
        }
        if (routeContext.originPoiId != null && routeContext.destinationPoiId != null) {
            return true;
        }
        String normalized = normalizeText(message);
        return isDestinationOnlyRouteText(message)
                || containsAny(normalized, "route", "directions", "to", "go", "\u8def\u7ebf", "\u5230", "\u53bb", "\u524d\u5f80", "\u5bfc\u822a");
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSmallTalk(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return true;
        }
        if (isCampusIntroQuestion(normalized)) {
            return true;
        }
        if (containsAny(normalized, "hello", "hi", "hey", "thanks", "thankyou", "help", "whoareyou",
                "\u4f60\u597d", "\u55e8", "\u54c8\u55bd", "\u8c22\u8c22", "\u591a\u8c22", "\u5e2e\u52a9", "\u600e\u4e48\u7528", "\u4f60\u662f\u8c01")) {
            return true;
        }
        return normalized.matches("[a-z?.!,']{1,12}")
                && !containsAny(normalized, "route", "directions", "recommend", "study", "library", "canteen", "print", "weather",
                "\u8def\u7ebf", "\u63a8\u8350", "\u81ea\u4e60", "\u56fe\u4e66\u9986", "\u98df\u5802", "\u6253\u5370", "\u5929\u6c14");
    }

    private boolean isCampusIntroQuestion(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        if (containsAny(normalized,
                "library", "canteen", "dining", "gymnasium", "building", "print", "shop", "clinic",
                "\u56fe\u4e66\u9986", "\u98df\u5802", "\u6253\u5370", "\u4f53\u80b2\u9986", "\u6559\u5b66\u697c", "\u697c")) {
            return false;
        }
        boolean asksIntro = containsAny(normalized,
                "introduce", "intro", "about", "tellmeabout", "whatis",
                "\u4ecb\u7ecd", "\u7b80\u4ecb", "\u4e86\u89e3", "\u662f\u4ec0\u4e48");
        boolean campusTarget = containsAny(normalized,
                "nuist", "smartcampus",
                "\u5357\u4eac\u4fe1\u606f\u5de5\u7a0b\u5927\u5b66", "\u5357\u4fe1\u5927", "\u5b66\u6821", "\u6821\u56ed");
        return asksIntro && campusTarget;
    }

    private boolean isNoteQuestion(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return containsAny(normalized,
                "note", "notes", "comment", "comments", "review", "reviews",
                "\u7b14\u8bb0", "\u8bc4\u8bba", "\u8bc4\u4ef7", "\u7ecf\u9a8c");
    }

    private boolean isRouteQuestion(String normalized) {
        if (hasPlainChineseRoutePattern(normalized)) {
            return true;
        }
        return (normalized.contains("从") && (normalized.contains("去") || normalized.contains("到")))
                || normalized.contains("路线")
                || normalized.contains("怎么走")
                || (normalized.contains("from") && (normalized.contains("to") || normalized.contains("go")))
                || normalized.contains("route")
                || normalized.contains("directions");
    }

    private boolean isRecommendationQuestion(String normalized) {
        return containsAny(normalized, "推荐", "安静", "插座", "自习", "学习", "少淋雨", "淋雨", "遮蔽", "夜间", "雨天友好",
                "recommend", "quiet", "outlet", "outlets", "study", "sheltered", "rain", "night",
                "sports", "sport", "gym", "basketball", "football", "soccer", "fitness", "stadium", "field", "court",
                "dining", "canteen", "restaurant", "food", "dinner", "lunch", "breakfast", "meal", "cuisine")
                || isWhereShouldGoQuestion(normalized)
                || isShoppingNeedQuestion(normalized);
    }

    private boolean isCurrentLocationRouteQuestion(String normalized) {
        return mentionsCurrentLocation(normalized)
                && containsAny(normalized,
                "to", "go", "route", "directions", "navigate", "navigation", "fromcurrentlocation", "fromhere",
                "\u5230", "\u53bb", "\u524d\u5f80", "\u5bfc\u822a", "\u8def\u7ebf", "\u600e\u4e48\u8d70", "\u4ece\u5f53\u524d\u4f4d\u7f6e");
    }

    private boolean isNearbyRecommendationQuestion(String normalized) {
        return mentionsNearby(normalized)
                && (isRecommendationQuestion(normalized)
                || containsAny(normalized,
                "find", "search", "nearest", "closest", "around", "nearby", "nearme",
                "\u627e", "\u9644\u8fd1", "\u5468\u8fb9", "\u8eab\u8fb9", "\u6700\u8fd1",
                "restaurant", "restaurants", "dining", "canteen", "study", "library", "shop", "store", "supermarket",
                "\u9910\u5385", "\u98df\u5802", "\u56fe\u4e66\u9986", "\u81ea\u4e60", "\u5546\u5e97", "\u8d85\u5e02"));
    }

    private boolean mentionsCurrentLocationOrNearby(String normalized) {
        return mentionsCurrentLocation(normalized) || mentionsNearby(normalized);
    }

    private boolean mentionsCurrentLocation(String normalized) {
        return containsAny(normalized,
                "currentlocation", "currentposition", "mylocation", "myposition", "fromhere",
                "\u5f53\u524d\u4f4d\u7f6e", "\u6211\u7684\u4f4d\u7f6e", "\u6211\u73b0\u5728\u7684\u4f4d\u7f6e", "\u6211\u8fd9\u91cc");
    }

    private boolean mentionsNearby(String normalized) {
        return containsAny(normalized,
                "nearby", "nearme", "aroundme", "nearest", "closest",
                "\u9644\u8fd1", "\u5468\u8fb9", "\u8eab\u8fb9", "\u6211\u9644\u8fd1", "\u79bb\u6211\u6700\u8fd1");
    }

    private String currentLocationRequestKind(String normalized) {
        return mentionsNearby(normalized) && !isCurrentLocationRouteQuestion(normalized) ? "nearby" : "route";
    }

    private boolean isRankedPlaceQuestion(String normalized) {
        boolean asksRank = containsAny(normalized,
                "top", "popular", "hot", "rank", "ranking", "mostpopular", "hottest", "placerank",
                "\u70ed\u95e8", "\u6700\u70ed\u95e8", "\u6392\u540d", "\u6392\u884c", "\u699c")
                || (normalized.contains("\u524d") && (firstArabicNumber(normalized) > 0 || chineseNumberLimit(normalized) > 0));
        boolean asksPlace = containsAny(normalized,
                "poi", "place", "places", "restaurant", "restaurants", "dining", "canteen", "canteens",
                "library", "study", "gym", "sports", "building", "service", "shop", "supermarket",
                "\u5730\u70b9", "\u5730\u65b9", "\u9910\u5385", "\u98df\u5802", "\u5403\u996d", "\u996d\u5e97",
                "\u56fe\u4e66\u9986", "\u81ea\u4e60", "\u5b66\u4e60", "\u4f53\u80b2", "\u8fd0\u52a8",
                "\u6559\u5b66\u697c", "\u697c", "\u670d\u52a1", "\u6253\u5370", "\u5546\u5e97", "\u8d85\u5e02");
        return asksRank && asksPlace;
    }

    private int requestedLimit(String normalized) {
        int number = firstArabicNumber(normalized);
        if (number <= 0) {
            number = chineseNumberLimit(normalized);
        }
        if (number <= 0) {
            number = 5;
        }
        return Math.min(Math.max(number, 1), 10);
    }

    private int firstArabicNumber(String normalized) {
        int current = 0;
        boolean seen = false;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isDigit(ch)) {
                seen = true;
                current = current * 10 + Character.digit(ch, 10);
            } else if (seen) {
                return current;
            }
        }
        return seen ? current : 0;
    }

    private int chineseNumberLimit(String normalized) {
        Map<String, Integer> numbers = Map.ofEntries(
                Map.entry("\u4e00", 1),
                Map.entry("\u4e8c", 2),
                Map.entry("\u4e24", 2),
                Map.entry("\u4e09", 3),
                Map.entry("\u56db", 4),
                Map.entry("\u4e94", 5),
                Map.entry("\u516d", 6),
                Map.entry("\u4e03", 7),
                Map.entry("\u516b", 8),
                Map.entry("\u4e5d", 9),
                Map.entry("\u5341", 10)
        );
        for (Map.Entry<String, Integer> entry : numbers.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private String rankedPlaceCategory(String normalized) {
        if (containsAny(normalized, "restaurant", "restaurants", "dining", "canteen", "canteens", "food", "cafe",
                "\u9910\u5385", "\u98df\u5802", "\u5403\u996d", "\u996d\u5e97", "\u5496\u5561")) {
            return "DINING";
        }
        if (containsAny(normalized, "library", "study", "quiet", "\u56fe\u4e66\u9986", "\u81ea\u4e60", "\u5b66\u4e60")) {
            return "STUDY";
        }
        if (containsAny(normalized, "gym", "sports", "sport", "basketball", "football", "soccer", "fitness", "stadium", "field", "court",
                "\u4f53\u80b2", "\u8fd0\u52a8", "\u7bee\u7403")) {
            return "SPORTS";
        }
        if (containsAny(normalized, "dorm", "dormitory", "\u5bbf\u820d")) {
            return "DORM";
        }
        if (containsAny(normalized, "teaching", "classroom", "building", "\u6559\u5b66\u697c", "\u6559\u5ba4", "\u697c")) {
            return "TEACHING";
        }
        if (containsAny(normalized, "service", "print", "shop", "supermarket", "atm", "bank",
                "\u670d\u52a1", "\u6253\u5370", "\u5546\u5e97", "\u8d85\u5e02", "\u94f6\u884c")) {
            return "SERVICE";
        }
        return null;
    }

    private boolean matchesRankedPlaceCategory(PoiEntity poi, String category, String normalized) {
        if (category == null) {
            return true;
        }
        if (category.equals(poi.category)) {
            return true;
        }
        String text = normalizeText(poi.name + "," + poi.category + "," + poi.tags);
        return switch (category) {
            case "DINING" -> containsAny(text, "dining", "canteen", "restaurant", "food", "cafe");
            case "STUDY" -> containsAny(text, "study", "library", "quiet");
            case "SPORTS" -> containsAny(text, "sports", "gym", "basketball", "football", "soccer", "fitness", "stadium", "field", "court", "track");
            case "DORM" -> containsAny(text, "dorm", "dormitory");
            case "TEACHING" -> containsAny(text, "teaching", "classroom", "building");
            case "SERVICE" -> containsAny(text, "service", "print", "shop", "supermarket", "atm", "bank");
            default -> false;
        };
    }

    private String rankedPlaceReply(String locale, int count, int requestedLimit, String category) {
        if ("en-US".equals(locale)) {
            return "I ranked campus places by Place Rank and highlighted the top " + count + ".";
        }
        String target = switch (category == null ? "" : category) {
            case "DINING" -> "\u9910\u5385";
            case "STUDY" -> "\u5b66\u4e60\u5730\u70b9";
            case "SPORTS" -> "\u8fd0\u52a8\u5730\u70b9";
            case "DORM" -> "\u5bbf\u820d";
            case "TEACHING" -> "\u6559\u5b66\u697c";
            case "SERVICE" -> "\u670d\u52a1\u5730\u70b9";
            default -> "\u5730\u70b9";
        };
        if (count == 0) {
            return "\u6682\u672a\u627e\u5230\u53ef\u6309 Place Rank \u6392\u5e8f\u7684" + target + "\u3002";
        }
        return "\u5df2\u6309 Place Rank \u4e3a\u4f60\u627e\u5230\u524d " + count + " \u4e2a\u70ed\u95e8" + target + "\u3002";
    }

    private List<String> extractPreferenceTags(String normalized) {
        List<String> tags = new ArrayList<>();
        addPlainChinesePreferenceTags(tags, normalized);
        if (containsAny(normalized, "安静", "自习", "学习")) {
            tags.add("quiet");
            tags.add("study");
        }
        if (containsAny(normalized, "quiet", "study")) {
            tags.add("quiet");
            tags.add("study");
        }
        if (normalized.contains("插座") || normalized.contains("outlet")) {
            tags.add("outlets");
        }
        if (containsAny(normalized, "淋雨", "遮蔽", "雨天", "sheltered", "rain")) {
            tags.add("sheltered");
        }
        if (normalized.contains("夜间") || normalized.contains("night")) {
            tags.add("night-access");
        }
        if (normalized.contains("打印") || normalized.contains("print")) {
            tags.add("print");
        }
        if (containsAny(normalized, "食堂", "吃饭", "dining", "canteen", "restaurant", "food", "dinner", "lunch", "breakfast", "meal", "cuisine")) {
            tags.add("dining");
            tags.add("canteen");
            if (containsAny(normalized, "dinner", "lunch", "breakfast")) {
                tags.add("dinner");
            }
        }
        if (containsAny(normalized, "sports", "sport", "gym", "basketball", "football", "soccer", "fitness", "stadium", "field", "court",
                "\u4f53\u80b2", "\u8fd0\u52a8", "\u7bee\u7403")) {
            tags.add("sports");
            if (containsAny(normalized, "basketball", "\u7bee\u7403")) {
                tags.add("basketball");
                tags.add("court");
            }
            if (containsAny(normalized, "football", "soccer", "field", "stadium")) {
                tags.add("field");
                tags.add("stadium");
            }
        }
        if (isShoppingNeedQuestion(normalized)) {
            tags.add("supermarket");
            tags.add("shopping");
            tags.add("daily-life");
        }
        return tags;
    }

    private boolean matchesRecommendation(PoiEntity poi, List<String> tags, String normalized) {
        if (tags.isEmpty()) {
            return "STUDY".equals(poi.category) || containsAny(normalizeText(poi.tags), "study", "quiet");
        }
        int score = scoreRecommendation(poi, tags, normalized);
        return score > 0;
    }

    private int scoreRecommendation(PoiEntity poi, List<String> tags, String normalized) {
        int score = 0;
        String text = normalizeText(poi.name + "," + poi.category + "," + poi.locationText + "," + poi.tags + "," + poi.remark);
        for (String tag : tags) {
            if (text.contains(normalizeText(tag))) {
                score += 3;
            }
        }
        if (containsAny(normalized, "自习", "学习", "安静") && "STUDY".equals(poi.category)) {
            score += 2;
        }
        if (containsAny(normalized, "少淋雨", "雨天", "遮蔽") && Boolean.TRUE.equals(poi.sheltered)) {
            score += 2;
        }
        if (containsAny(normalized, "打印", "print") && containsAny(text, "print", "copy", "binding")) {
            score += 2;
        }
        if (isShoppingNeedQuestion(normalized) && containsAny(text, "supermarket", "shopping", "daily-life")) {
            score += 2;
        }
        if (isSportsNeedQuestion(normalized) && "SPORTS".equals(poi.category)) {
            score += 2;
        }
        if (containsAny(normalized, "basketball", "\u7bee\u7403") && containsAny(text, "basketball", "court", "gym")) {
            score += 2;
        }
        if (containsAny(normalized, "football", "soccer") && containsAny(text, "field", "stadium", "sports", "track")) {
            score += 2;
        }
        if (isDiningNeedQuestion(normalized) && "DINING".equals(poi.category)) {
            score += 2;
        }
        return score;
    }

    private String recommendationReply(String locale, String normalized) {
        if (isSportsNeedQuestion(normalized)) {
            return text(locale,
                    "\u5df2\u6309\u8fd0\u52a8\u3001\u7403\u573a\u548c\u4f53\u80b2\u8bbe\u65bd\u6807\u7b7e\u7b5b\u9009\u5730\u70b9\uff0c\u5e76\u5df2\u540c\u6b65\u5230\u5730\u56fe\u3002",
                    "I found campus sports places that match your activity request and highlighted them on the map.");
        }
        if (isDiningNeedQuestion(normalized)) {
            return text(locale,
                    "\u5df2\u6309\u9910\u996e\u548c\u98df\u5802\u573a\u666f\u7b5b\u9009\u5730\u70b9\uff0c\u5e76\u5df2\u540c\u6b65\u5230\u5730\u56fe\u3002",
                    "I found campus dining options that match your request and highlighted them on the map.");
        }
        return text(locale,
                "\u5df2\u6309\u6821\u56ed\u573a\u666f\u6807\u7b7e\u7b5b\u9009\u5019\u9009\u5730\u70b9\uff0c\u63a8\u8350\u7ed3\u679c\u5df2\u540c\u6b65\u5230\u5730\u56fe\u3002",
                "I filtered candidate places by campus scenario tags. The recommendations are synced to the map.");
    }

    private boolean isSportsNeedQuestion(String normalized) {
        return containsAny(normalized,
                "sports", "sport", "gym", "basketball", "football", "soccer", "fitness", "stadium", "field", "court",
                "\u4f53\u80b2", "\u8fd0\u52a8", "\u7bee\u7403");
    }

    private boolean isDiningNeedQuestion(String normalized) {
        return containsAny(normalized,
                "dining", "canteen", "restaurant", "food", "dinner", "lunch", "breakfast", "meal", "cuisine",
                "\u98df\u5802", "\u9910\u5385", "\u5403\u996d");
    }

    private List<PoiEntity> matchPoisInMessage(String message, List<PoiEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        String normalized = normalizeText(message);
        List<PoiEntity> matched = new ArrayList<>(candidates.stream()
                .filter(poi -> normalized.contains(normalizeText(poi.name)))
                .sorted(Comparator.comparingInt(poi -> normalized.indexOf(normalizeText(poi.name))))
                .toList());
        addPlainChineseAliasMatches(matched, candidates, normalized);
        addPlainChineseAliasMatches(matched, candidates, normalized, Map.ofEntries(
                Map.entry("\u56fe\u4e66\u9986", "library"),
                Map.entry("\u81ea\u4e60", "study"),
                Map.entry("\u5b66\u4e60", "study"),
                Map.entry("\u98df\u5802", "canteen"),
                Map.entry("\u9910\u5385", "dining"),
                Map.entry("\u5546\u5e97", "shop"),
                Map.entry("\u8d85\u5e02", "supermarket"),
                Map.entry("\u4f53\u80b2\u9986", "gym"),
                Map.entry("\u7bee\u7403", "basketball"),
                Map.entry("\u6253\u5370", "print")
        ));
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("西苑宿舍", "Xiyuan"),
                Map.entry("东苑宿舍", "Dongyuan"),
                Map.entry("宿舍a区", "Xiyuan"),
                Map.entry("宿舍A区", "Xiyuan"),
                Map.entry("宿舍", "dorm"),
                Map.entry("图书馆", "Library"),
                Map.entry("自习区", "study"),
                Map.entry("教学楼", "teaching"),
                Map.entry("明德楼", "Mingde"),
                Map.entry("文德楼", "Wende"),
                Map.entry("\u5c1a\u8d24\u697c", "Shangxian"),
                Map.entry("打印店", "print"),
                Map.entry("食堂", "canteen"),
                Map.entry("中苑", "Zhongyuan"),
                Map.entry("西苑", "Xiyuan"),
                Map.entry("风云剧场", "Fengyun"),
                Map.entry("校医院", "clinic"),
                Map.entry("xiyuandormitory", "Xiyuan"),
                Map.entry("xiyuan", "Xiyuan"),
                Map.entry("dongyuan", "Dongyuan"),
                Map.entry("dorma", "Xiyuan"),
                Map.entry("dorm", "dorm"),
                Map.entry("library", "Library"),
                Map.entry("studyarea", "study"),
                Map.entry("study", "study"),
                Map.entry("printshop", "print"),
                Map.entry("printer", "print"),
                Map.entry("mingde", "Mingde"),
                Map.entry("wende", "Wende"),
                Map.entry("shangxian", "Shangxian"),
                Map.entry("zhongyuan", "Zhongyuan"),
                Map.entry("canteen", "canteen"),
                Map.entry("clinic", "clinic"),
                Map.entry("hospital", "clinic"),
                Map.entry("fengyun", "Fengyun"),
                Map.entry("gymnasium", "gym"),
                Map.entry("gym", "gym"),
                Map.entry("observationfield", "observation")
        );
        for (Map.Entry<String, String> entry : orderedAliases(aliases)) {
            if (normalized.contains(normalizeText(entry.getKey()))) {
                if (aliasCoveredByMatchedPoi(matched, entry.getKey(), entry.getValue(), normalized)) {
                    continue;
                }
                candidates.stream()
                        .filter(poi -> normalizeText(poi.name + poi.tags).contains(normalizeText(entry.getValue())))
                        .findFirst()
                        .ifPresent(poi -> {
                            if (matched.stream().noneMatch(item -> item.id.equals(poi.id))) {
                                matched.add(poi);
                            }
                        });
            }
        }
        return matched.stream()
                .sorted(Comparator.comparingInt(poi -> routeMentionIndex(poi, normalized)))
                .limit(6)
                .toList();
    }

    private List<Map.Entry<String, String>> orderedAliases(Map<String, String> aliases) {
        return aliases.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> entry) -> normalizeText(entry.getKey()).length()).reversed())
                .toList();
    }

    private boolean aliasCoveredByMatchedPoi(List<PoiEntity> matched, String alias, String value, String normalized) {
        String aliasText = normalizeText(alias);
        String valueText = normalizeText(value);
        if (!normalized.contains(aliasText)) {
            return false;
        }
        boolean broadAlias = isBroadPoiAlias(valueText);
        return matched.stream().anyMatch(poi -> {
            String poiName = normalizeText(poi.name);
            String poiText = normalizeText(poi.name + "," + poi.category + "," + poi.tags);
            if (!poiText.contains(valueText)) {
                return false;
            }
            if (!poiName.isBlank() && normalized.contains(poiName)) {
                return true;
            }
            return broadAlias && routeMentionIndex(poi, normalized) != Integer.MAX_VALUE;
        });
    }

    private boolean isBroadPoiAlias(String value) {
        return List.of("canteen", "dining", "study", "teaching", "dorm", "print", "clinic", "hospital", "library")
                .contains(value);
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String extractKeyword(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String plainKeyword = extractPlainChineseKeyword(message);
        if (!plainKeyword.isBlank()) {
            return plainKeyword;
        }
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("图书馆", "Library"),
                Map.entry("自习", "study"),
                Map.entry("学习", "study"),
                Map.entry("食堂", "canteen"),
                Map.entry("餐厅", "canteen"),
                Map.entry("打印", "print"),
                Map.entry("复印", "print"),
                Map.entry("校医院", "clinic"),
                Map.entry("医院", "clinic"),
                Map.entry("教学楼", "teaching"),
                Map.entry("教室", "classroom"),
                Map.entry("实验楼", "teaching"),
                Map.entry("明德楼", "Mingde"),
                Map.entry("文德楼", "Wende"),
                Map.entry("\u5c1a\u8d24\u697c", "Shangxian"),
                Map.entry("西苑", "Xiyuan"),
                Map.entry("东苑", "Dongyuan"),
                Map.entry("中苑", "Zhongyuan"),
                Map.entry("风云剧场", "Fengyun"),
                Map.entry("宿舍", "dorm"),
                Map.entry("快递", "express"),
                Map.entry("行政", "student-affairs"),
                Map.entry("服务中心", "service"),
                Map.entry("体育馆", "gym"),
                Map.entry("library", "Library"),
                Map.entry("study", "study"),
                Map.entry("print", "print"),
                Map.entry("printer", "print"),
                Map.entry("canteen", "canteen"),
                Map.entry("dining", "dining"),
                Map.entry("xiyuan", "Xiyuan"),
                Map.entry("dongyuan", "Dongyuan"),
                Map.entry("zhongyuan", "Zhongyuan"),
                Map.entry("mingde", "Mingde"),
                Map.entry("wende", "Wende"),
                Map.entry("shangxian", "Shangxian"),
                Map.entry("hospital", "clinic"),
                Map.entry("clinic", "clinic"),
                Map.entry("teaching", "teaching"),
                Map.entry("classroom", "classroom"),
                Map.entry("dorm", "dorm"),
                Map.entry("express", "express"),
                Map.entry("fengyun", "Fengyun"),
                Map.entry("gym", "gym"),
                Map.entry("observation", "observation")
        );
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (normalizeText(message).contains(normalizeText(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return message.replace("找", "").replace("哪里", "").replace("在哪", "").trim();
    }

    private int routeMentionIndex(PoiEntity poi, String normalized) {
        String searchable = normalizeText(poi.name + "," + poi.tags);
        List<String> markers = new ArrayList<>();
        markers.add(poi.name);
        if (containsAny(searchable, "xiyuan", "西苑宿舍")) {
            markers.addAll(List.of("西苑宿舍", "宿舍a区", "宿舍", "xiyuan", "dorma", "dorm"));
        }
        if (containsAny(searchable, "dongyuan", "东苑宿舍")) {
            markers.addAll(List.of("东苑宿舍", "宿舍", "dongyuan", "dorm"));
        }
        if (containsAny(searchable, "library", "图书馆")) {
            markers.addAll(List.of("图书馆", "library"));
        }
        if (containsAny(searchable, "mingde", "明德楼")) {
            markers.addAll(List.of("明德楼", "mingde", "教学楼"));
        }
        if (containsAny(searchable, "wende", "文德楼")) {
            markers.addAll(List.of("文德楼", "wende", "教学楼"));
        }
        if (containsAny(searchable, "shangxian", "\u5c1a\u8d24\u697c")) {
            markers.addAll(List.of("\u5c1a\u8d24\u697c", "\u5c1a\u8d24", "shangxian", "teaching"));
        }
        if (containsAny(searchable, "print", "打印")) {
            markers.addAll(List.of("打印店", "打印", "printshop", "printer", "print"));
        }
        if (containsAny(searchable, "canteen", "食堂")) {
            markers.addAll(List.of("中苑", "西苑", "食堂", "canteen", "dining"));
        }
        if (containsAny(searchable, "clinic", "校医院")) {
            markers.addAll(List.of("校医院", "医院", "clinic", "hospital"));
        }
        int best = Integer.MAX_VALUE;
        for (String marker : markers) {
            int index = normalized.indexOf(normalizeText(marker));
            if (index >= 0 && index < best) {
                best = index;
            }
        }
        return best;
    }

    private boolean isPlainChineseRouteQuestion(String normalized) {
        if (hasPlainChineseRoutePattern(normalized)) {
            return true;
        }
        return (normalized.contains("从") && (normalized.contains("去") || normalized.contains("到")))
                || normalized.contains("路线")
                || normalized.contains("怎么走");
    }

    private boolean hasPlainChineseRoutePattern(String normalized) {
        return (normalized.contains("\u4ece") && containsAny(normalized, "\u5230", "\u53bb", "\u524d\u5f80", "\u5f80"))
                || containsAny(normalized, "\u8def\u7ebf", "\u600e\u4e48\u8d70", "\u5bfc\u822a", "\u8def\u5f84");
    }

    private boolean isPlainChineseRecommendationQuestion(String normalized) {
        return containsAny(normalized, "推荐", "安静", "插座", "自习", "学习", "少淋雨", "淋雨", "遮蔽", "夜间", "雨天友好")
                || isWhereShouldGoQuestion(normalized)
                || isShoppingNeedQuestion(normalized);
    }

    private void addPlainChinesePreferenceTags(List<String> tags, String normalized) {
        if (containsAny(normalized, "安静", "自习", "学习")) {
            tags.add("quiet");
            tags.add("study");
        }
        if (normalized.contains("插座")) {
            tags.add("outlets");
        }
        if (containsAny(normalized, "淋雨", "遮蔽", "雨天", "少淋雨")) {
            tags.add("sheltered");
        }
        if (normalized.contains("夜间")) {
            tags.add("night-access");
        }
        if (normalized.contains("打印")) {
            tags.add("print");
        }
        if (normalized.contains("食堂") || normalized.contains("吃饭")) {
            tags.add("dining");
        }
        if (isShoppingNeedQuestion(normalized)) {
            tags.add("supermarket");
            tags.add("shopping");
            tags.add("daily-life");
        }
    }

    private boolean isWhereShouldGoQuestion(String normalized) {
        return containsAny(normalized,
                "去哪里", "去哪", "到哪里", "应该去", "可以去", "适合去", "哪里可以买", "哪儿可以买",
                "whereshouldigo", "wheretogo", "wherecanigo", "wherecanibuy", "whereshouldibuy");
    }

    private boolean isShoppingNeedQuestion(String normalized) {
        boolean shoppingTarget = containsAny(normalized,
                "买", "购买", "商店", "超市", "便利店", "小卖部", "购物", "日用品",
                "方便面", "泡面", "零食", "饮料", "矿泉水",
                "buy", "shopping", "supermarket", "store", "convenience", "snack", "drink", "noodle", "instantnoodle");
        boolean asksForPlace = containsAny(normalized,
                "找", "想", "要", "需要", "哪里", "哪儿", "去哪", "去哪里", "应该去",
                "find", "need", "want", "where", "go");
        return shoppingTarget && asksForPlace;
    }

    private void addPlainChineseAliasMatches(List<PoiEntity> matched, List<PoiEntity> candidates, String normalized) {
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("西苑宿舍", "Xiyuan"),
                Map.entry("东苑宿舍", "Dongyuan"),
                Map.entry("宿舍a区", "Xiyuan"),
                Map.entry("宿舍a", "Xiyuan"),
                Map.entry("宿舍", "dorm"),
                Map.entry("图书馆", "Library"),
                Map.entry("自习区", "study"),
                Map.entry("自习", "study"),
                Map.entry("教学楼", "teaching"),
                Map.entry("明德楼", "Mingde"),
                Map.entry("文德楼", "Wende"),
                Map.entry("\u5c1a\u8d24\u697c", "Shangxian"),
                Map.entry("打印店", "print"),
                Map.entry("打印", "print"),
                Map.entry("食堂", "canteen"),
                Map.entry("中苑", "Zhongyuan"),
                Map.entry("西苑", "Xiyuan"),
                Map.entry("风云剧场", "Fengyun"),
                Map.entry("校医院", "clinic")
        );
        for (Map.Entry<String, String> entry : orderedAliases(aliases)) {
            if (normalized.contains(normalizeText(entry.getKey()))) {
                if (aliasCoveredByMatchedPoi(matched, entry.getKey(), entry.getValue(), normalized)) {
                    continue;
                }
                candidates.stream()
                        .filter(poi -> normalizeText(poi.name + poi.tags).contains(normalizeText(entry.getValue())))
                        .findFirst()
                        .ifPresent(poi -> {
                            if (matched.stream().noneMatch(item -> item.id.equals(poi.id))) {
                                matched.add(poi);
                            }
                        });
            }
        }
    }

    private void addPlainChineseAliasMatches(List<PoiEntity> matched, List<PoiEntity> candidates, String normalized, Map<String, String> aliases) {
        for (Map.Entry<String, String> entry : orderedAliases(aliases)) {
            if (normalized.contains(normalizeText(entry.getKey()))) {
                candidates.stream()
                        .filter(poi -> normalizeText(poi.name + "," + poi.category + "," + poi.tags).contains(normalizeText(entry.getValue())))
                        .findFirst()
                        .ifPresent(poi -> {
                            if (matched.stream().noneMatch(item -> item.id.equals(poi.id))) {
                                matched.add(poi);
                            }
                        });
            }
        }
    }

    private String extractPlainChineseKeyword(String message) {
        String normalized = normalizeText(message);
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("图书馆", "Library"),
                Map.entry("自习", "study"),
                Map.entry("学习", "study"),
                Map.entry("食堂", "canteen"),
                Map.entry("餐厅", "canteen"),
                Map.entry("打印", "print"),
                Map.entry("复印", "print"),
                Map.entry("校医院", "clinic"),
                Map.entry("医院", "clinic"),
                Map.entry("教学楼", "teaching"),
                Map.entry("教室", "classroom"),
                Map.entry("实验楼", "teaching"),
                Map.entry("明德楼", "Mingde"),
                Map.entry("文德楼", "Wende"),
                Map.entry("\u5c1a\u8d24\u697c", "Shangxian"),
                Map.entry("西苑", "Xiyuan"),
                Map.entry("东苑", "Dongyuan"),
                Map.entry("中苑", "Zhongyuan"),
                Map.entry("风云剧场", "Fengyun"),
                Map.entry("宿舍", "dorm"),
                Map.entry("快递", "express"),
                Map.entry("行政", "student-affairs"),
                Map.entry("服务中心", "service"),
                Map.entry("体育馆", "gym")
        );
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (normalized.contains(normalizeText(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return "";
    }

    private String text(String locale, String zh, String en) {
        return "en-US".equals(locale) ? en : zh;
    }
}
