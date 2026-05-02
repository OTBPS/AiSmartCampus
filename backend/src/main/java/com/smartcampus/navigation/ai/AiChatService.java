package com.smartcampus.navigation.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcampus.navigation.common.BizException;
import com.smartcampus.navigation.discover.DiscoverPostResponse;
import com.smartcampus.navigation.discover.DiscoverService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiChatService {
    private final MockAiService mockAiService;
    private final DeepSeekAiService deepSeekAiService;
    private final AiMessageMapper aiMessageMapper;
    private final ObjectMapper objectMapper;
    private final RouteWeatherEnhancer routeWeatherEnhancer;
    private final DiscoverService discoverService;
    private final String provider;

    public AiChatService(
            MockAiService mockAiService,
            DeepSeekAiService deepSeekAiService,
            AiMessageMapper aiMessageMapper,
            ObjectMapper objectMapper,
            RouteWeatherEnhancer routeWeatherEnhancer,
            DiscoverService discoverService,
            @Value("${app.ai.provider:mock}") String provider
    ) {
        this.mockAiService = mockAiService;
        this.deepSeekAiService = deepSeekAiService;
        this.aiMessageMapper = aiMessageMapper;
        this.objectMapper = objectMapper;
        this.routeWeatherEnhancer = routeWeatherEnhancer;
        this.discoverService = discoverService;
        this.provider = provider == null ? "mock" : provider;
    }

    public AiChatResponse chat(Long userId, String message, String locale) {
        return chat(userId, message, locale, null);
    }

    public AiChatResponse chat(Long userId, String message, String locale, AiChatRequest.RouteContext routeContext) {
        AiChatResponse response = resolveResponse(userId, message, locale, routeContext);
        saveLog(userId, message, response);
        return response;
    }

    public AiChatResponse preview(String message, String locale) {
        return preview(message, locale, null);
    }

    public AiChatResponse preview(String message, String locale, AiChatRequest.RouteContext routeContext) {
        return resolveResponse(null, message, locale, routeContext);
    }

    public java.util.List<AiMessageEntity> logs() {
        return aiMessageMapper.selectList(new QueryWrapper<AiMessageEntity>().orderByDesc("created_at").last("LIMIT 100"));
    }

    public java.util.List<AiMessageEntity> logsByUser(Long userId) {
        return aiMessageMapper.selectList(new QueryWrapper<AiMessageEntity>()
                .eq("user_id", userId)
                .orderByDesc("created_at")
                .last("LIMIT 30"));
    }

    public int clearLogsByUser(Long userId) {
        return aiMessageMapper.delete(new QueryWrapper<AiMessageEntity>().eq("user_id", userId));
    }

    private AiChatResponse resolveResponse(Long userId, String message, String locale, AiChatRequest.RouteContext routeContext) {
        AiChatResponse response;
        if ("deepseek".equalsIgnoreCase(provider) && deepSeekAiService.isConfigured()) {
            AiChatResponse ruleResponse = mockPreview(message, locale, routeContext);
            try {
                response = deepSeekChat(message, locale, routeContext);
                if (needsFallback(response) || !matchesRuleIntent(ruleResponse, response)) {
                    response = ruleResponse;
                }
            } catch (RuntimeException ex) {
                response = ruleResponse;
            }
        } else {
            response = mockPreview(message, locale, routeContext);
        }
        response = enrichNoteSearch(response, userId, message, locale);
        return routeWeatherEnhancer.enhance(response, locale);
    }

    private AiChatResponse mockPreview(String message, String locale, AiChatRequest.RouteContext routeContext) {
        return routeContext == null
                ? mockAiService.preview(message, locale)
                : mockAiService.preview(message, locale, routeContext);
    }

    private AiChatResponse deepSeekChat(String message, String locale, AiChatRequest.RouteContext routeContext) {
        return routeContext == null
                ? deepSeekAiService.chat(message, locale)
                : deepSeekAiService.chat(message, locale, routeContext);
    }

    private boolean needsFallback(AiChatResponse response) {
        return response == null
                || "unknown".equals(response.intent)
                || response.mapActions == null
                || response.mapActions.isEmpty();
    }

    private boolean matchesRuleIntent(AiChatResponse ruleResponse, AiChatResponse response) {
        if (ruleResponse == null || response == null) {
            return false;
        }
        if ("route_help".equals(ruleResponse.intent) || "recommend_place".equals(ruleResponse.intent)) {
            return ruleResponse.intent.equals(response.intent);
        }
        if ("small_talk".equals(ruleResponse.intent)) {
            return ruleResponse.intent.equals(response.intent);
        }
        if ("find_note".equals(ruleResponse.intent)) {
            return ruleResponse.intent.equals(response.intent);
        }
        return true;
    }

    private AiChatResponse enrichNoteSearch(AiChatResponse response, Long userId, String message, String locale) {
        if (response == null || !"find_note".equals(response.intent)) {
            return response;
        }
        String keyword = extractNoteKeyword(message);
        List<DiscoverPostResponse> notes = discoverService.searchPublishedNotes(keyword, userId, 5);
        response.notes = new ArrayList<>(notes);
        response.pois = new ArrayList<>();
        response.toolCalls = new ArrayList<>();
        response.mapActions = new ArrayList<>();
        response.reply = noteReply(locale, notes.size(), keyword);
        return response;
    }

    private String noteReply(String locale, int count, String keyword) {
        boolean english = "en-US".equals(locale);
        if (count == 0) {
            return english
                    ? "No related discover notes were found."
                    : "\u6682\u672a\u627e\u5230\u76f8\u5173 note\u3002";
        }
        if (StringUtils.hasText(keyword)) {
            return english
                    ? "I found " + count + " related discover notes."
                    : "\u627e\u5230 " + count + " \u6761\u76f8\u5173 note\u3002";
        }
        return english
                ? "I found " + count + " popular discover notes."
                : "\u627e\u5230 " + count + " \u6761\u70ed\u95e8 note\u3002";
    }

    private String extractNoteKeyword(String message) {
        String keyword = message == null ? "" : message.trim();
        if (!StringUtils.hasText(keyword)) {
            return "";
        }
        int marker = firstIndexAfter(keyword, "\u5173\u4e8e", "\u6709\u5173", "about", "for", "on");
        if (marker >= 0 && marker < keyword.length()) {
            keyword = keyword.substring(marker);
        }
        keyword = keyword.replaceAll("(?i)\\b(show|find|search|look|related|about|for|on|place|poi|notes?|comments?|reviews?)\\b", " ");
        keyword = keyword.replace("\u6211\u60f3", " ")
                .replace("\u60f3", " ")
                .replace("\u627e\u4e00\u4e0b", " ")
                .replace("\u627e", " ")
                .replace("\u67e5\u770b", " ")
                .replace("\u770b\u770b", " ")
                .replace("\u770b", " ")
                .replace("\u67e5", " ")
                .replace("\u5173\u4e8e", " ")
                .replace("\u6709\u5173", " ")
                .replace("\u5730\u70b9", " ")
                .replace("\u5730\u65b9", " ")
                .replace("\u7684", " ")
                .replace("\u7b14\u8bb0", " ")
                .replace("\u8bc4\u8bba", " ")
                .replace("\u8bc4\u4ef7", " ")
                .replace("\u7ecf\u9a8c", " ")
                .replace("\u76f8\u5173", " ")
                .replace("\u4e00\u4e0b", " ");
        return keyword.replaceAll("[,，。.?？:：/\\\\]+", " ").trim().replaceAll("\\s+", " ");
    }

    private int firstIndexAfter(String text, String... markers) {
        String lower = text.toLowerCase(Locale.ROOT);
        int best = Integer.MAX_VALUE;
        int length = 0;
        for (String marker : markers) {
            String normalizedMarker = marker.toLowerCase(Locale.ROOT);
            int index = lower.indexOf(normalizedMarker);
            if (index >= 0 && index < best) {
                best = index;
                length = marker.length();
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best + length;
    }

    private void saveLog(Long userId, String message, AiChatResponse response) {
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
            throw new BizException("AI interaction log save failed");
        }
    }
}
