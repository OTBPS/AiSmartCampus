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

    public AiChatResponse chat(Long userId, String message) {
        AiChatResponse response = buildResponse(message);
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
        return buildResponse(message);
    }

    public List<AiMessageEntity> logs() {
        return aiMessageMapper.selectList(new QueryWrapper<AiMessageEntity>().orderByDesc("created_at").last("LIMIT 100"));
    }

    private AiChatResponse buildResponse(String message) {
        String normalized = normalizeText(message);
        if (isRouteQuestion(normalized)) {
            return routeHelp(message);
        }
        if (isRecommendationQuestion(normalized)) {
            return recommendPlace(message);
        }
        return findPoi(message);
    }

    private AiChatResponse findPoi(String message) {
        String keyword = extractKeyword(message);
        List<PoiEntity> pois = poiService.list(keyword, null, null, true);
        if (pois.isEmpty()) {
            pois = poiService.list(null, null, null, true).stream().limit(3).toList();
        }
        AiChatResponse response = base("find_poi", "我根据你的问题找到了这些校园地点，并已在地图上高亮。", pois);
        response.toolCalls.add(new AiChatResponse.ToolCall("searchPoi", Map.of("keyword", keyword)));
        response.mapActions.add(highlight(pois));
        if (!pois.isEmpty()) {
            response.mapActions.add(openDetail(pois.get(0).id));
        }
        return response;
    }

    private AiChatResponse recommendPlace(String message) {
        String normalized = normalizeText(message);
        List<String> tags = extractPreferenceTags(normalized);
        String tag = tags.isEmpty() ? "自习" : tags.get(0);
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
        AiChatResponse response = base("recommend_place", "我已按安静、自习、插座、雨天遮蔽等校园场景标签筛选候选地点，推荐结果已同步到地图。", pois);
        response.toolCalls.add(new AiChatResponse.ToolCall("searchPoiByTags", Map.of("tags", tags)));
        response.mapActions.add(highlight(pois));
        if (!pois.isEmpty()) {
            response.mapActions.add(openDetail(pois.get(0).id));
        }
        return response;
    }

    private AiChatResponse routeHelp(String message) {
        List<PoiEntity> all = poiService.list(null, null, null, true);
        List<PoiEntity> matched = matchPoisInMessage(message, all);
        if (matched.size() < 2) {
            matched = all.stream().limit(2).toList();
        }
        String from = matched.isEmpty() ? "起点" : matched.get(0).name;
        String to = matched.size() < 2 ? "终点" : matched.get(1).name;
        AiChatResponse response = base("route_help", "我已识别从 " + from + " 到 " + to + " 的路线意图，并准备触发普通路线兜底。第一版不做复杂路线优化，但会保留地点上下文。", matched);
        List<Long> ids = matched.stream().map(poi -> poi.id).toList();
        response.toolCalls.add(new AiChatResponse.ToolCall("planCampusRouteFallback", Map.of("poiIds", ids)));
        AiChatResponse.MapAction action = new AiChatResponse.MapAction("draw_route");
        action.poiIds = ids;
        action.routeMode = "AMAP_FALLBACK";
        action.payload = new LinkedHashMap<>();
        action.payload.put("reason", "普通路线兜底");
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

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRouteQuestion(String normalized) {
        return (normalized.contains("从") && (normalized.contains("去") || normalized.contains("到")))
                || normalized.contains("路线")
                || normalized.contains("怎么走");
    }

    private boolean isRecommendationQuestion(String normalized) {
        return containsAny(normalized, "推荐", "安静", "插座", "自习", "学习", "少淋雨", "淋雨", "遮蔽", "夜间", "雨天友好");
    }

    private List<String> extractPreferenceTags(String normalized) {
        List<String> tags = new ArrayList<>();
        if (containsAny(normalized, "安静", "自习", "学习")) {
            tags.add("安静");
        }
        if (normalized.contains("插座")) {
            tags.add("有插座");
        }
        if (containsAny(normalized, "淋雨", "遮蔽", "雨天")) {
            tags.add("遮蔽");
        }
        if (normalized.contains("夜间")) {
            tags.add("夜间可达");
        }
        if (normalized.contains("打印")) {
            tags.add("打印");
        }
        if (normalized.contains("食堂") || normalized.contains("吃饭")) {
            tags.add("就餐");
        }
        return tags;
    }

    private boolean matchesRecommendation(PoiEntity poi, List<String> tags, String normalized) {
        if (tags.isEmpty()) {
            return "STUDY".equals(poi.category) || containsAny(poi.tags, "自习", "安静");
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
        if (normalized.contains("打印") && containsAny(text, "打印", "复印")) {
            score += 2;
        }
        return score;
    }

    private List<PoiEntity> matchPoisInMessage(String message, List<PoiEntity> candidates) {
        String normalized = normalizeText(message);
        List<PoiEntity> matched = new ArrayList<>(candidates.stream()
                .filter(poi -> normalized.contains(normalizeText(poi.name)))
                .sorted(Comparator.comparingInt(poi -> normalized.indexOf(normalizeText(poi.name))))
                .toList());
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("宿舍a区", "宿舍 A 区"),
                Map.entry("宿舍A区", "宿舍 A 区"),
                Map.entry("宿舍", "宿舍"),
                Map.entry("图书馆", "图书馆"),
                Map.entry("自习区", "自习"),
                Map.entry("教学楼", "教学楼"),
                Map.entry("打印店", "打印"),
                Map.entry("食堂", "食堂"),
                Map.entry("校医院", "校医院")
        );
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (normalized.contains(normalizeText(entry.getKey()))) {
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
        return matched.stream().limit(2).toList();
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String extractKeyword(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        Map<String, String> aliases = Map.ofEntries(
                Map.entry("图书馆", "图书馆"),
                Map.entry("自习", "自习"),
                Map.entry("学习", "自习"),
                Map.entry("食堂", "食堂"),
                Map.entry("餐厅", "餐厅"),
                Map.entry("打印", "打印"),
                Map.entry("复印", "打印"),
                Map.entry("校医院", "校医院"),
                Map.entry("医院", "医院"),
                Map.entry("教学楼", "教学楼"),
                Map.entry("教室", "教学楼"),
                Map.entry("实验楼", "实验楼"),
                Map.entry("宿舍", "宿舍"),
                Map.entry("快递", "快递"),
                Map.entry("行政", "行政"),
                Map.entry("服务中心", "服务中心"),
                Map.entry("体育馆", "体育馆")
        );
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (message.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return message.replace("找", "").replace("哪里", "").replace("在哪", "").trim();
    }
}
