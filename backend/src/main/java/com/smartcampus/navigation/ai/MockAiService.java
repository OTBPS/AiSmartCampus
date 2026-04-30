package com.smartcampus.navigation.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcampus.navigation.common.BizException;
import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiService;
import java.util.ArrayList;
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
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (normalized.contains("从") && normalized.contains("去")) {
            return routeHelp(message);
        }
        if (containsAny(normalized, "安静", "插座", "自习", "推荐", "少淋雨", "遮蔽")) {
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
        List<String> tags = new ArrayList<>();
        if (message.contains("安静") || message.contains("自习")) {
            tags.add("安静");
        }
        if (message.contains("插座")) {
            tags.add("有插座");
        }
        if (message.contains("淋雨") || message.contains("遮蔽")) {
            tags.add("遮蔽");
        }
        String tag = tags.isEmpty() ? "自习" : tags.get(0);
        List<PoiEntity> pois = poiService.list(null, null, tag, true);
        if (pois.isEmpty()) {
            pois = poiService.list(null, "STUDY", null, true);
        }
        AiChatResponse response = base("recommend_place", "我优先根据地点标签和校园场景约束筛选候选地点，推荐结果已同步到地图。", pois);
        response.toolCalls.add(new AiChatResponse.ToolCall("searchPoiByTags", Map.of("tags", tags)));
        response.mapActions.add(highlight(pois));
        if (!pois.isEmpty()) {
            response.mapActions.add(openDetail(pois.get(0).id));
        }
        return response;
    }

    private AiChatResponse routeHelp(String message) {
        List<PoiEntity> all = poiService.list(null, null, null, true);
        List<PoiEntity> matched = all.stream().filter(poi -> message.contains(poi.name)).toList();
        if (matched.size() < 2) {
            matched = all.stream().limit(2).toList();
        }
        AiChatResponse response = base("route_help", "我已识别起终点并准备触发普通路线兜底。第一版不做复杂路线优化，但会保留推荐理由和地点上下文。", matched);
        List<Long> ids = matched.stream().map(poi -> poi.id).toList();
        response.toolCalls.add(new AiChatResponse.ToolCall("planCampusRouteFallback", Map.of("poiIds", ids)));
        AiChatResponse.MapAction action = new AiChatResponse.MapAction("draw_route");
        action.poiIds = ids;
        action.routeMode = "AMAP_FALLBACK";
        action.payload = new LinkedHashMap<>();
        action.payload.put("reason", "普通路线兜底");
        response.mapActions.add(action);
        response.mapActions.add(highlight(matched));
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

    private String extractKeyword(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        Map<String, String> aliases = Map.of(
                "图书馆", "图书馆",
                "食堂", "食堂",
                "打印", "打印",
                "校医院", "校医院",
                "教学楼", "教学楼",
                "宿舍", "宿舍"
        );
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            if (message.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return message.replace("找", "").replace("哪里", "").trim();
    }
}
