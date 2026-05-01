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
        AiChatResponse response = buildResponse(message, locale);
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
        return buildResponse(message, "zh-CN");
    }

    public AiChatResponse preview(String message, String locale) {
        return buildResponse(message, locale);
    }

    public List<AiMessageEntity> logs() {
        return aiMessageMapper.selectList(new QueryWrapper<AiMessageEntity>().orderByDesc("created_at").last("LIMIT 100"));
    }

    private AiChatResponse buildResponse(String message, String locale) {
        String normalized = normalizeText(message);
        if (isRouteQuestion(normalized) || isPlainChineseRouteQuestion(normalized)) {
            return routeHelp(message, locale);
        }
        if (isRecommendationQuestion(normalized) || isPlainChineseRecommendationQuestion(normalized)) {
            return recommendPlace(message, locale);
        }
        return findPoi(message, locale);
    }

    private AiChatResponse findPoi(String message, String locale) {
        String keyword = extractKeyword(message);
        List<PoiEntity> pois = poiService.list(keyword, null, null, true);
        if (pois.isEmpty()) {
            pois = poiService.list(null, null, null, true).stream().limit(3).toList();
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
        AiChatResponse response = base("recommend_place", text(locale,
                "我已按安静、自习、插座、雨天遮蔽等校园场景标签筛选候选地点，推荐结果已同步到地图。",
                "I filtered candidate places by campus tags such as quiet study, outlets, and sheltered routes. The recommendations are synced to the map."), pois);
        response.toolCalls.add(new AiChatResponse.ToolCall("searchPoiByTags", Map.of("tags", tags)));
        response.mapActions.add(highlight(pois));
        if (!pois.isEmpty()) {
            response.mapActions.add(openDetail(pois.get(0).id));
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
                || normalized.contains("怎么走")
                || (normalized.contains("from") && (normalized.contains("to") || normalized.contains("go")))
                || normalized.contains("route")
                || normalized.contains("directions");
    }

    private boolean isRecommendationQuestion(String normalized) {
        return containsAny(normalized, "推荐", "安静", "插座", "自习", "学习", "少淋雨", "淋雨", "遮蔽", "夜间", "雨天友好",
                "recommend", "quiet", "outlet", "outlets", "study", "sheltered", "rain", "night");
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
        if (normalized.contains("食堂") || normalized.contains("吃饭") || normalized.contains("dining") || normalized.contains("canteen")) {
            tags.add("dining");
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
        return score;
    }

    private List<PoiEntity> matchPoisInMessage(String message, List<PoiEntity> candidates) {
        String normalized = normalizeText(message);
        List<PoiEntity> matched = new ArrayList<>(candidates.stream()
                .filter(poi -> normalized.contains(normalizeText(poi.name)))
                .sorted(Comparator.comparingInt(poi -> normalized.indexOf(normalizeText(poi.name))))
                .toList());
        addPlainChineseAliasMatches(matched, candidates, normalized);
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
                Map.entry("zhongyuan", "Zhongyuan"),
                Map.entry("canteen", "canteen"),
                Map.entry("clinic", "clinic"),
                Map.entry("hospital", "clinic"),
                Map.entry("fengyun", "Fengyun"),
                Map.entry("gymnasium", "gym"),
                Map.entry("gym", "gym"),
                Map.entry("observationfield", "observation")
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
        return matched.stream()
                .sorted(Comparator.comparingInt(poi -> routeMentionIndex(poi, normalized)))
                .limit(2)
                .toList();
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
        return (normalized.contains("从") && (normalized.contains("去") || normalized.contains("到")))
                || normalized.contains("路线")
                || normalized.contains("怎么走");
    }

    private boolean isPlainChineseRecommendationQuestion(String normalized) {
        return containsAny(normalized, "推荐", "安静", "插座", "自习", "学习", "少淋雨", "淋雨", "遮蔽", "夜间", "雨天友好");
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
                Map.entry("打印店", "print"),
                Map.entry("打印", "print"),
                Map.entry("食堂", "canteen"),
                Map.entry("中苑", "Zhongyuan"),
                Map.entry("西苑", "Xiyuan"),
                Map.entry("风云剧场", "Fengyun"),
                Map.entry("校医院", "clinic")
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
