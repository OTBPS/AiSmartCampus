package com.smartcampus.navigation.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcampus.navigation.discover.DiscoverPostResponse;
import com.smartcampus.navigation.discover.DiscoverService;
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

    @Test
    void findNoteIntentIsEnrichedWithDiscoverResults() {
        MockAiService mockAiService = mock(MockAiService.class);
        DeepSeekAiService deepSeekAiService = mock(DeepSeekAiService.class);
        DiscoverService discoverService = mock(DiscoverService.class);
        AiChatResponse ruleResponse = new AiChatResponse();
        ruleResponse.intent = "find_note";
        ruleResponse.reply = "Searching notes";
        DiscoverPostResponse note = new DiscoverPostResponse();
        note.id = 3L;
        note.title = "Library note";
        when(mockAiService.preview(eq("show notes about library"), eq("en-US"))).thenReturn(ruleResponse);
        when(discoverService.searchPublishedNotes(eq("library"), eq(null), eq(5))).thenReturn(List.of(note));

        AiChatResponse response = service(mockAiService, deepSeekAiService, discoverService).preview("show notes about library", "en-US");

        assertEquals("find_note", response.intent);
        assertEquals(List.of(note), response.notes);
        assertTrue(response.pois.isEmpty());
        assertTrue(response.mapActions.isEmpty());
        assertEquals("I found 1 related discover notes.", response.reply);
    }

    private AiChatService service(MockAiService mockAiService, DeepSeekAiService deepSeekAiService) {
        return service(mockAiService, deepSeekAiService, mock(DiscoverService.class));
    }

    private AiChatService service(MockAiService mockAiService, DeepSeekAiService deepSeekAiService, DiscoverService discoverService) {
        RouteWeatherEnhancer routeWeatherEnhancer = mock(RouteWeatherEnhancer.class);
        when(routeWeatherEnhancer.enhance(any(AiChatResponse.class), any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new AiChatService(
                mockAiService,
                deepSeekAiService,
                mock(AiMessageMapper.class),
                new ObjectMapper(),
                routeWeatherEnhancer,
                discoverService,
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
