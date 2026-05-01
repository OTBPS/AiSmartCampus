package com.smartcampus.navigation.ai;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcampus.navigation.poi.PoiEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiChatServiceTest {
    @Test
    void deepSeekInvalidJsonFallsBackToMockResponse() {
        MockAiService mockAiService = mock(MockAiService.class);
        DeepSeekAiService deepSeekAiService = mock(DeepSeekAiService.class);
        AiChatResponse fallback = fallbackPrinterResponse();
        when(mockAiService.preview(eq("找打印店"), eq("zh-CN"))).thenReturn(fallback);
        when(deepSeekAiService.isConfigured()).thenReturn(true);
        when(deepSeekAiService.chat(eq("找打印店"), eq("zh-CN")))
                .thenThrow(new IllegalStateException("invalid_deepseek_json"));

        AiChatResponse response = service(mockAiService, deepSeekAiService).preview("找打印店", "zh-CN");

        assertSame(fallback, response);
    }

    @Test
    void deepSeekMissingMapActionsFallsBackToMockResponse() {
        MockAiService mockAiService = mock(MockAiService.class);
        DeepSeekAiService deepSeekAiService = mock(DeepSeekAiService.class);
        AiChatResponse fallback = fallbackPrinterResponse();
        AiChatResponse deepSeekResponse = new AiChatResponse();
        deepSeekResponse.intent = "find_poi";
        deepSeekResponse.reply = "I found Campus Print Shop.";
        deepSeekResponse.pois = List.of(printer());
        when(mockAiService.preview(eq("Find Campus Print Shop"), eq("en-US"))).thenReturn(fallback);
        when(deepSeekAiService.isConfigured()).thenReturn(true);
        when(deepSeekAiService.chat(eq("Find Campus Print Shop"), eq("en-US"))).thenReturn(deepSeekResponse);

        AiChatResponse response = service(mockAiService, deepSeekAiService).preview("Find Campus Print Shop", "en-US");

        assertSame(fallback, response);
    }

    @Test
    void deepSeekUnknownIntentFallsBackToMockResponse() {
        MockAiService mockAiService = mock(MockAiService.class);
        DeepSeekAiService deepSeekAiService = mock(DeepSeekAiService.class);
        AiChatResponse fallback = fallbackPrinterResponse();
        AiChatResponse deepSeekResponse = fallbackPrinterResponse();
        deepSeekResponse.intent = "unknown";
        when(mockAiService.preview(eq("Find Campus Print Shop"), eq("en-US"))).thenReturn(fallback);
        when(deepSeekAiService.isConfigured()).thenReturn(true);
        when(deepSeekAiService.chat(eq("Find Campus Print Shop"), eq("en-US"))).thenReturn(deepSeekResponse);

        AiChatResponse response = service(mockAiService, deepSeekAiService).preview("Find Campus Print Shop", "en-US");

        assertSame(fallback, response);
    }

    private AiChatService service(MockAiService mockAiService, DeepSeekAiService deepSeekAiService) {
        return new AiChatService(
                mockAiService,
                deepSeekAiService,
                mock(AiMessageMapper.class),
                new ObjectMapper(),
                "deepseek"
        );
    }

    private AiChatResponse fallbackPrinterResponse() {
        AiChatResponse response = new AiChatResponse();
        response.intent = "find_poi";
        response.reply = "Mock fallback";
        response.pois = List.of(printer());
        AiChatResponse.MapAction highlight = new AiChatResponse.MapAction("highlight_pois");
        highlight.poiIds = List.of(9L);
        response.mapActions.add(highlight);
        response.mapActions.add(new AiChatResponse.MapAction("open_poi_detail"));
        response.mapActions.get(1).poiId = 9L;
        response.toolCalls.add(new AiChatResponse.ToolCall("searchPoi", java.util.Map.of("keyword", "print")));
        return response;
    }

    private PoiEntity printer() {
        PoiEntity poi = new PoiEntity();
        poi.id = 9L;
        poi.name = "Campus Print Shop";
        poi.category = "SERVICE";
        poi.tags = "print,copy,service";
        poi.enabled = true;
        return poi;
    }
}
