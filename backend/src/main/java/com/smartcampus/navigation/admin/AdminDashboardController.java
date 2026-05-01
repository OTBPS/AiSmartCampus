package com.smartcampus.navigation.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcampus.navigation.ai.AiMessageEntity;
import com.smartcampus.navigation.ai.AiMessageMapper;
import com.smartcampus.navigation.common.ApiResponse;
import com.smartcampus.navigation.discover.DiscoverPostEntity;
import com.smartcampus.navigation.discover.DiscoverPostMapper;
import com.smartcampus.navigation.feedback.FeedbackEntity;
import com.smartcampus.navigation.feedback.FeedbackMapper;
import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {
    private final PoiMapper poiMapper;
    private final FeedbackMapper feedbackMapper;
    private final AiMessageMapper aiMessageMapper;
    private final DiscoverPostMapper discoverPostMapper;
    private final ObjectMapper objectMapper;

    public AdminDashboardController(
            PoiMapper poiMapper,
            FeedbackMapper feedbackMapper,
            AiMessageMapper aiMessageMapper,
            DiscoverPostMapper discoverPostMapper,
            ObjectMapper objectMapper
    ) {
        this.poiMapper = poiMapper;
        this.feedbackMapper = feedbackMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.discoverPostMapper = discoverPostMapper;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> dashboard() {
        DashboardResponse response = new DashboardResponse();
        response.poiCount = poiMapper.selectCount(new QueryWrapper<PoiEntity>());
        response.pendingFeedbackCount = feedbackMapper.selectCount(new QueryWrapper<FeedbackEntity>().eq("status", "PENDING"));
        response.aiQueryCount = aiMessageMapper.selectCount(new QueryWrapper<AiMessageEntity>());
        response.discoverPostCount = discoverPostMapper.selectCount(new QueryWrapper<DiscoverPostEntity>());
        List<AiMessageEntity> aiLogs = aiMessageMapper.selectList(
                new QueryWrapper<AiMessageEntity>().orderByDesc("created_at").last("LIMIT 100")
        );
        List<FeedbackEntity> feedbackItems = feedbackMapper.selectList(new QueryWrapper<FeedbackEntity>());
        List<PoiEntity> pois = poiMapper.selectList(new QueryWrapper<PoiEntity>());

        response.aiIntentStats = statItems(
                aiLogs.stream()
                        .map(log -> normalizeKey(log.intent, "unknown"))
                        .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting())),
                response.aiQueryCount,
                this::intentLabel
        );
        response.mapActionStats = statItems(
                aiLogs.stream()
                        .flatMap(log -> mapActionTypes(log.mapActionsJson).stream())
                        .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting())),
                aiLogs.stream().mapToLong(log -> mapActionTypes(log.mapActionsJson).size()).sum(),
                this::mapActionLabel
        );
        response.feedbackStatusStats = statItems(
                feedbackItems.stream()
                        .map(item -> normalizeKey(item.status, "UNKNOWN"))
                        .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting())),
                feedbackItems.size(),
                this::feedbackStatusLabel
        );
        response.hotPois = hotPois(pois, feedbackItems, aiLogs);
        return ApiResponse.ok(response);
    }

    private List<DashboardResponse.StatItem> statItems(
            Map<String, Long> counts,
            long total,
            Function<String, String> labeler
    ) {
        long base = Math.max(total, counts.values().stream().mapToLong(Long::longValue).sum());
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new DashboardResponse.StatItem(
                        entry.getKey(),
                        labeler.apply(entry.getKey()),
                        entry.getValue(),
                        base == 0 ? 0 : (int) Math.round(entry.getValue() * 100.0 / base)
                ))
                .toList();
    }

    private List<DashboardResponse.HotPoi> hotPois(
            List<PoiEntity> pois,
            List<FeedbackEntity> feedbackItems,
            List<AiMessageEntity> aiLogs
    ) {
        Map<Long, PoiEntity> poiById = pois.stream()
                .filter(poi -> poi.id != null)
                .collect(Collectors.toMap(poi -> poi.id, Function.identity(), (left, right) -> left));
        Map<Long, Long> heatByPoi = new LinkedHashMap<>();
        feedbackItems.stream()
                .map(feedback -> feedback.poiId)
                .filter(Objects::nonNull)
                .forEach(poiId -> heatByPoi.merge(poiId, 2L, Long::sum));
        aiLogs.stream()
                .flatMap(log -> mapActionPoiIds(log.mapActionsJson).stream())
                .forEach(poiId -> heatByPoi.merge(poiId, 1L, Long::sum));

        return pois.stream()
                .filter(poi -> poi.id != null)
                .sorted(Comparator.comparingLong((PoiEntity poi) -> heatByPoi.getOrDefault(poi.id, 0L)).reversed()
                        .thenComparing(poi -> poi.name == null ? "" : poi.name))
                .limit(5)
                .map(poi -> new DashboardResponse.HotPoi(
                        poi.id,
                        poi.name,
                        poi.category,
                        poi.openStatus,
                        heatByPoi.getOrDefault(poi.id, 0L)
                ))
                .filter(item -> poiById.containsKey(item.id))
                .toList();
    }

    private List<String> mapActionTypes(String mapActionsJson) {
        List<String> types = new ArrayList<>();
        for (JsonNode action : readArray(mapActionsJson)) {
            String type = action.path("type").asText("");
            if (!type.isBlank()) {
                types.add(type);
            }
        }
        return types;
    }

    private List<Long> mapActionPoiIds(String mapActionsJson) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode action : readArray(mapActionsJson)) {
            JsonNode poiId = action.path("poiId");
            if (poiId.canConvertToLong()) {
                ids.add(poiId.asLong());
            }
            JsonNode poiIds = action.path("poiIds");
            if (poiIds.isArray()) {
                poiIds.forEach(id -> {
                    if (id.canConvertToLong()) {
                        ids.add(id.asLong());
                    }
                });
            }
        }
        return ids;
    }

    private Iterable<JsonNode> readArray(String json) {
        try {
            JsonNode node = objectMapper.readTree(json == null ? "[]" : json);
            return node.isArray() ? node : List.of();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String normalizeKey(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String intentLabel(String intent) {
        return switch (intent) {
            case "find_poi" -> "找地点";
            case "recommend_place" -> "条件推荐";
            case "route_help" -> "路线帮助";
            default -> "未识别";
        };
    }

    private String mapActionLabel(String action) {
        return switch (action) {
            case "highlight_pois" -> "高亮地点";
            case "open_poi_detail" -> "打开详情";
            case "draw_route" -> "路线兜底";
            default -> action.toLowerCase(Locale.ROOT);
        };
    }

    private String feedbackStatusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "待审核";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            default -> "未知";
        };
    }
}
