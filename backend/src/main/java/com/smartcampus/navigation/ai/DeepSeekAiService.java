package com.smartcampus.navigation.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class DeepSeekAiService {
    private static final Set<String> INTENTS = Set.of("find_poi", "recommend_place", "route_help", "find_note", "small_talk", "unknown");
    private static final Set<String> MAP_ACTIONS = Set.of("highlight_pois", "open_poi_detail", "draw_route");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final PoiService poiService;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;

    public DeepSeekAiService(
            PoiService poiService,
            ObjectMapper objectMapper,
            @Value("${app.ai.deepseek.api-key:}") String apiKey,
            @Value("${app.ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${app.ai.deepseek.model:deepseek-chat}") String model,
            @Value("${app.ai.deepseek.timeout-seconds:20}") int timeoutSeconds
    ) {
        this.poiService = poiService;
        this.objectMapper = objectMapper;
        this.apiKey = normalizeApiKey(apiKey);
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    static String normalizeApiKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        int keyStart = trimmed.indexOf("sk-");
        return keyStart > 0 ? trimmed.substring(keyStart).trim() : trimmed;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    public AiChatResponse chat(String message, String locale) {
        return chat(message, locale, null);
    }

    public AiChatResponse chat(String message, String locale, AiChatRequest.RouteContext routeContext) {
        List<PoiEntity> pois = poiService.list(null, null, null, true);
        JsonNode result = client().post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody(message, locale, pois, routeContext))
                .retrieve()
                .body(JsonNode.class);
        String content = result == null ? "" : result.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("empty_deepseek_response");
        }
        return parseStructuredResponse(content, locale, pois);
    }

    public AiChatResponse smallTalk(String message, String locale) {
        JsonNode result = client().post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(smallTalkRequestBody(message, locale))
                .retrieve()
                .body(JsonNode.class);
        String content = result == null ? "" : result.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("empty_deepseek_response");
        }
        return parseSmallTalkResponse(content, locale);
    }

    private RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    private Map<String, Object> requestBody(String message, String locale, List<PoiEntity> pois, AiChatRequest.RouteContext routeContext) {
        return Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt(locale, pois)),
                        Map.of("role", "user", "content", userPrompt(message, routeContext, pois))
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.1,
                "stream", false
        );
    }

    private Map<String, Object> smallTalkRequestBody(String message, String locale) {
        String language = "en-US".equals(locale) ? "English" : "Chinese";
        return Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "You are SmartCampusNavigation's campus chat assistant. Reply in " + language + ". "
                                        + "Return only valid JSON with schema {\"intent\":\"small_talk\",\"reply\":\"text\"}. "
                                        + "Answer general campus questions directly, including NUIST facts, nearby transit, app capability questions, greetings, and thanks. "
                                        + "Do not plan map actions, do not search POIs, and do not include poiIds, toolCalls, or mapActions. "
                                        + "Keep reply within 100 characters. If unsure, say so briefly."),
                        Map.of("role", "user", "content", message == null ? "" : message)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.3,
                "max_tokens", 160,
                "stream", false
        );
    }

    private String systemPrompt(String locale, List<PoiEntity> pois) {
        String language = "en-US".equals(locale) ? "English" : "Chinese";
        StringBuilder builder = new StringBuilder();
        builder.append("You are the map-action planner for SmartCampusNavigation. ");
        builder.append("Reply in ").append(language).append(". ");
        builder.append("Return only valid JSON, no markdown. ");
        builder.append("Use only POI IDs from the campus POI list. ");
        builder.append("The JSON schema is: ");
        builder.append("{\"intent\":\"find_poi|recommend_place|route_help|find_note|small_talk|unknown\",");
        builder.append("\"reply\":\"short user-facing message\",");
        builder.append("\"poiIds\":[1,2],");
        builder.append("\"toolCalls\":[{\"tool\":\"searchPoi|searchPoiByTags|searchPoiByRank|planCampusRouteFallback\",\"arguments\":{}}],");
        builder.append("\"mapActions\":[{\"type\":\"highlight_pois\",\"poiIds\":[1]},");
        builder.append("{\"type\":\"open_poi_detail\",\"poiId\":1},");
        builder.append("{\"type\":\"draw_route\",\"poiIds\":[1,2],\"routeMode\":\"AMAP_FALLBACK\",");
        builder.append("\"payload\":{\"from\":\"origin name\",\"to\":\"destination name\",\"reason\":\"standard route fallback\"}}]}.");
        builder.append(" For greetings, thanks, capability questions, or other small talk, use intent small_talk, reply briefly, and return empty poiIds, toolCalls, and mapActions. ");
        builder.append(" For requests about campus notes, comments, reviews, or discover posts for a place, use intent find_note, reply briefly, and return empty poiIds, toolCalls, and mapActions. ");
        builder.append(" For route questions, return draw_route.poiIds in route order: origin, waypoints, destination. ");
        builder.append("If currentLocation is supplied and the user asks to route from current location, keep draw_route.poiIds to real destination/waypoint POI IDs only and include payload.startPoint with longitude, latitude, label, and coordinateSystem. ");
        builder.append("If the user asks for nearby places and currentLocation is supplied, prefer nearest matching POIs by distance. ");
        builder.append("For popular, hottest, top N, ranking, Place Rank, or map rank requests, use intent recommend_place, choose matching POIs sorted by mapRank ascending where a smaller non-null mapRank is more popular, and return at most the requested N. ");
        builder.append("For user needs like buying snacks, instant noodles, drinks, daily supplies, shopping, supermarket, store, or asking where to go for them, use intent recommend_place and choose supermarket or shopping POIs. ");
        builder.append("If map route context is supplied, use text-mentioned origin/destination first, fill missing route endpoints from context, keep context waypoint order, append extra text waypoints, and de-duplicate. ");
        builder.append("For draw_route payload, include from, to, via as an array of waypoint names, reason, and source as text|map_context|mixed. ");
        builder.append("Campus POIs:\n");
        for (PoiEntity poi : pois) {
            builder.append("- id=").append(poi.id)
                    .append(", name=").append(poi.name)
                    .append(", category=").append(poi.category)
                    .append(", location=").append(nullToEmpty(poi.locationText))
                    .append(", status=").append(nullToEmpty(poi.openStatus))
                    .append(", mapRank=").append(poi.mapRank == null ? "" : poi.mapRank)
                    .append(", tags=").append(nullToEmpty(poi.tags))
                    .append(", sheltered=").append(Boolean.TRUE.equals(poi.sheltered))
                    .append(", remark=").append(nullToEmpty(poi.remark))
                    .append("\n");
        }
        return builder.toString();
    }

    private String userPrompt(String message, AiChatRequest.RouteContext routeContext, List<PoiEntity> pois) {
        StringBuilder builder = new StringBuilder();
        builder.append("User message: ").append(message == null ? "" : message).append("\n");
        if (routeContext != null) {
            builder.append("Current map route context POI IDs: ");
            builder.append("originPoiId=").append(routeContext.originPoiId).append(", ");
            builder.append("destinationPoiId=").append(routeContext.destinationPoiId).append(", ");
            builder.append("waypointPoiIds=").append(routeContext.waypointPoiIds == null ? List.of() : routeContext.waypointPoiIds).append(".\n");
            if (routeContext.currentLocation != null) {
                builder.append("Current user location: ");
                builder.append("longitude=").append(routeContext.currentLocation.longitude).append(", ");
                builder.append("latitude=").append(routeContext.currentLocation.latitude).append(", ");
                builder.append("accuracyMeters=").append(routeContext.currentLocation.accuracyMeters).append(", ");
                builder.append("label=").append(routeContext.currentLocation.label).append(", ");
                builder.append("coordinateSystem=").append(routeContext.currentLocation.coordinateSystem == null ? "GCJ02" : routeContext.currentLocation.coordinateSystem).append(".\n");
            }
            builder.append("Ignore IDs that are not present in the campus POI list. ");
            builder.append("If enough valid endpoints exist, produce route_help with draw_route.");
        }
        return builder.toString();
    }

    AiChatResponse parseStructuredResponse(String content, String locale, List<PoiEntity> allPois) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(content));
            Map<Long, PoiEntity> byId = new LinkedHashMap<>();
            for (PoiEntity poi : allPois) {
                byId.put(poi.id, poi);
            }

            AiChatResponse response = new AiChatResponse();
            String intent = root.path("intent").asText("unknown");
            response.intent = INTENTS.contains(intent) ? intent : "unknown";
            response.reply = root.path("reply").asText(defaultReply(locale));

            Set<Long> selectedIds = new LinkedHashSet<>(readIds(root.path("poiIds"), byId));
            response.toolCalls.addAll(readToolCalls(root.path("toolCalls")));
            response.mapActions.addAll(readMapActions(root.path("mapActions"), byId, selectedIds));

            if (selectedIds.isEmpty()) {
                collectIdsFromActions(response.mapActions, selectedIds);
            }
            response.pois = selectedIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
            response.reply = normalizeReplyLanguage(response.reply, response.intent, locale, response.pois);
            if (response.toolCalls.isEmpty()) {
                AiChatResponse.ToolCall fallbackToolCall = deriveToolCall(response.intent, selectedIds);
                if (fallbackToolCall != null) {
                    response.toolCalls.add(fallbackToolCall);
                }
            }
            if (!StringUtils.hasText(response.reply)) {
                response.reply = defaultReply(locale);
            }
            response.reply = limitSmallTalkReply(response.intent, response.reply);
            if ("small_talk".equals(response.intent)) {
                response.pois = List.of();
                response.toolCalls.clear();
                response.mapActions.clear();
            }
            return response;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("invalid_deepseek_json", ex);
        }
    }

    AiChatResponse parseSmallTalkResponse(String content, String locale) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(content));
            AiChatResponse response = new AiChatResponse();
            response.intent = "small_talk";
            String reply = root.path("reply").asText("");
            if (!StringUtils.hasText(reply)) {
                reply = root.path("answer").asText(defaultSmallTalkReply(locale));
            }
            response.reply = limitText(reply, 100);
            response.pois = List.of();
            response.toolCalls.clear();
            response.mapActions.clear();
            return response;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("invalid_deepseek_json", ex);
        }
    }

    private List<AiChatResponse.ToolCall> readToolCalls(JsonNode node) {
        List<AiChatResponse.ToolCall> calls = new ArrayList<>();
        if (!node.isArray()) return calls;
        for (JsonNode item : node) {
            String tool = item.path("tool").asText("");
            if (!StringUtils.hasText(tool)) continue;
            Map<String, Object> arguments = item.has("arguments")
                    ? objectMapper.convertValue(item.path("arguments"), MAP_TYPE)
                    : Map.of();
            calls.add(new AiChatResponse.ToolCall(tool, arguments));
        }
        return calls;
    }

    private List<AiChatResponse.MapAction> readMapActions(JsonNode node, Map<Long, PoiEntity> byId, Set<Long> selectedIds) {
        List<AiChatResponse.MapAction> actions = new ArrayList<>();
        if (!node.isArray()) return actions;
        for (JsonNode item : node) {
            String type = item.path("type").asText("");
            if (!MAP_ACTIONS.contains(type)) continue;
            AiChatResponse.MapAction action = new AiChatResponse.MapAction(type);
            if ("highlight_pois".equals(type) || "draw_route".equals(type)) {
                action.poiIds = readIds(item.path("poiIds"), byId);
                if ("highlight_pois".equals(type) && action.poiIds.isEmpty()) {
                    continue;
                }
                selectedIds.addAll(action.poiIds);
            }
            if ("open_poi_detail".equals(type)) {
                Long poiId = readId(item.path("poiId"), byId);
                if (poiId == null) continue;
                action.poiId = poiId;
                selectedIds.add(poiId);
            }
            if ("draw_route".equals(type)) {
                if (action.poiIds.size() < 2) continue;
                action.routeMode = item.path("routeMode").asText("AMAP_FALLBACK");
                action.payload = item.has("payload") ? objectMapper.convertValue(item.path("payload"), MAP_TYPE) : Map.of();
            }
            actions.add(action);
        }
        return actions;
    }

    private List<Long> readIds(JsonNode node, Map<Long, PoiEntity> byId) {
        List<Long> ids = new ArrayList<>();
        if (!node.isArray()) return ids;
        for (JsonNode item : node) {
            Long id = readId(item, byId);
            if (id != null && !ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    private Long readId(JsonNode node, Map<Long, PoiEntity> byId) {
        Long id = null;
        if (node.canConvertToLong()) {
            id = node.asLong();
        } else if (node.isTextual()) {
            try {
                id = Long.parseLong(node.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return id != null && byId.containsKey(id) ? id : null;
    }

    private AiChatResponse.ToolCall deriveToolCall(String intent, Set<Long> ids) {
        if ("recommend_place".equals(intent)) {
            return new AiChatResponse.ToolCall("searchPoiByTags", Map.of("poiIds", ids));
        }
        if ("route_help".equals(intent)) {
            return new AiChatResponse.ToolCall("planCampusRouteFallback", Map.of("poiIds", ids));
        }
        if ("small_talk".equals(intent) || "find_note".equals(intent)) {
            return null;
        }
        return new AiChatResponse.ToolCall("searchPoi", Map.of("poiIds", ids));
    }

    private void collectIdsFromActions(List<AiChatResponse.MapAction> actions, Set<Long> selectedIds) {
        for (AiChatResponse.MapAction action : actions) {
            selectedIds.addAll(action.poiIds);
            if (action.poiId != null) selectedIds.add(action.poiId);
        }
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        return trimmed;
    }

    private String defaultReply(String locale) {
        return "en-US".equals(locale) ? "I found matching campus map actions." : "已生成对应的校园地图动作。";
    }

    private String defaultSmallTalkReply(String locale) {
        return "en-US".equals(locale)
                ? "I can answer campus questions briefly."
                : "\u6211\u53ef\u4ee5\u7b80\u8981\u56de\u7b54\u6821\u56ed\u76f8\u5173\u95ee\u9898\u3002";
    }

    private String limitSmallTalkReply(String intent, String reply) {
        return "small_talk".equals(intent) ? limitText(reply, 100) : reply;
    }

    private String limitText(String text, int maxCodePoints) {
        if (text == null) {
            return "";
        }
        if (text.codePointCount(0, text.length()) <= maxCodePoints) {
            return text;
        }
        return text.codePoints()
                .limit(maxCodePoints)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .trim();
    }

    private String normalizeReplyLanguage(String reply, String intent, String locale, List<PoiEntity> pois) {
        if (!"en-US".equals(locale) || !containsHan(reply)) {
            return reply;
        }
        int count = pois.size();
        if ("route_help".equals(intent) && count >= 2) {
            return "I found the origin and destination and prepared the AMap standard route fallback.";
        }
        if ("recommend_place".equals(intent)) {
            return "I found matching campus recommendations and highlighted them on the map.";
        }
        if ("find_poi".equals(intent)) {
            return count == 1
                    ? "I found one matching campus place and opened its details on the map."
                    : "I found matching campus places and highlighted them on the map.";
        }
        return defaultReply(locale);
    }

    private boolean containsHan(String text) {
        return text != null && text.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.replace("\n", " ");
    }
}
