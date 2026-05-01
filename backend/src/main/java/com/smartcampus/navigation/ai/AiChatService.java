package com.smartcampus.navigation.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcampus.navigation.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {
    private final MockAiService mockAiService;
    private final DeepSeekAiService deepSeekAiService;
    private final AiMessageMapper aiMessageMapper;
    private final ObjectMapper objectMapper;
    private final String provider;

    public AiChatService(
            MockAiService mockAiService,
            DeepSeekAiService deepSeekAiService,
            AiMessageMapper aiMessageMapper,
            ObjectMapper objectMapper,
            @Value("${app.ai.provider:mock}") String provider
    ) {
        this.mockAiService = mockAiService;
        this.deepSeekAiService = deepSeekAiService;
        this.aiMessageMapper = aiMessageMapper;
        this.objectMapper = objectMapper;
        this.provider = provider == null ? "mock" : provider;
    }

    public AiChatResponse chat(Long userId, String message, String locale) {
        AiChatResponse response = resolveResponse(message, locale);
        saveLog(userId, message, response);
        return response;
    }

    public AiChatResponse preview(String message, String locale) {
        return resolveResponse(message, locale);
    }

    public java.util.List<AiMessageEntity> logs() {
        return aiMessageMapper.selectList(new QueryWrapper<AiMessageEntity>().orderByDesc("created_at").last("LIMIT 100"));
    }

    private AiChatResponse resolveResponse(String message, String locale) {
        if ("deepseek".equalsIgnoreCase(provider) && deepSeekAiService.isConfigured()) {
            AiChatResponse ruleResponse = mockAiService.preview(message, locale);
            try {
                AiChatResponse response = deepSeekAiService.chat(message, locale);
                if (!needsFallback(response) && matchesRuleIntent(ruleResponse, response)) {
                    return response;
                }
            } catch (RuntimeException ex) {
                return ruleResponse;
            }
            return ruleResponse;
        }
        return mockAiService.preview(message, locale);
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
        return true;
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
