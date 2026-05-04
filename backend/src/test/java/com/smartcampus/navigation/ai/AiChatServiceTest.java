package com.smartcampus.navigation.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void deepSeekSmallTalkWithoutMapActionsIsKept() {
        MockAiService mockAiService = mock(MockAiService.class);
        DeepSeekAiService deepSeekAiService = mock(DeepSeekAiService.class);
        AiChatResponse ruleResponse = smallTalkResponse("Local NUIST intro.");
        AiChatResponse deepSeekResponse = new AiChatResponse();
        deepSeekResponse.intent = "small_talk";
        deepSeekResponse.reply = "NUIST is a university in Nanjing.";
        when(mockAiService.preview(eq("Introduce NUIST"), eq("en-US"))).thenReturn(ruleResponse);
        when(deepSeekAiService.isConfigured()).thenReturn(true);
        when(deepSeekAiService.smallTalk(eq("Introduce NUIST"), eq("en-US"))).thenReturn(deepSeekResponse);

        AiChatResponse response = service(mockAiService, deepSeekAiService).preview("Introduce NUIST", "en-US");

        assertSame(deepSeekResponse, response);
        assertEquals("small_talk", response.intent);
        assertTrue(response.mapActions.isEmpty());
    }

    @Test
    void deepSeekSmallTalkCanReplaceNoResultFindPoiFallback() {
        MockAiService mockAiService = mock(MockAiService.class);
        DeepSeekAiService deepSeekAiService = mock(DeepSeekAiService.class);
        AiChatResponse ruleResponse = noResultFindPoiResponse();
        AiChatResponse deepSeekResponse = smallTalkResponse("NUIST is a university in Nanjing.");
        when(mockAiService.preview(eq("Introduce NUIST"), eq("en-US"))).thenReturn(ruleResponse);
        when(deepSeekAiService.isConfigured()).thenReturn(true);
        when(deepSeekAiService.smallTalk(eq("Introduce NUIST"), eq("en-US"))).thenReturn(deepSeekResponse);

        AiChatResponse response = service(mockAiService, deepSeekAiService).preview("Introduce NUIST", "en-US");

        assertSame(deepSeekResponse, response);
        assertEquals("small_talk", response.intent);
    }

    @Test
    void placeRecommendationQuestionUsesDeepSeekMapPlannerInsteadOfSmallTalk() {
        MockAiService mockAiService = mock(MockAiService.class);
        DeepSeekAiService deepSeekAiService = mock(DeepSeekAiService.class);
        String message = "\u6211\u60f3\u4e70\u4e00\u4e9b\u65b9\u4fbf\u9762,\u6211\u5e94\u8be5\u53bb\u54ea\u91cc";
        AiChatResponse ruleResponse = noResultFindPoiResponse();
        AiChatResponse deepSeekResponse = recommendShoppingResponse();
        when(mockAiService.preview(eq(message), eq("zh-CN"))).thenReturn(ruleResponse);
        when(deepSeekAiService.isConfigured()).thenReturn(true);
        when(deepSeekAiService.chat(eq(message), eq("zh-CN"))).thenReturn(deepSeekResponse);

        AiChatResponse response = service(mockAiService, deepSeekAiService).preview(message, "zh-CN");

        assertSame(deepSeekResponse, response);
        assertEquals("recommend_place", response.intent);
        verify(deepSeekAiService).chat(eq(message), eq("zh-CN"));
        verify(deepSeekAiService, never()).smallTalk(eq(message), eq("zh-CN"));
    }

    @Test
    void rankedPlaceRuleBypassesDeepSeekForStableMapRankOrdering() {
        MockAiService mockAiService = mock(MockAiService.class);
        DeepSeekAiService deepSeekAiService = mock(DeepSeekAiService.class);
        String message = "\u6211\u60f3\u627e\u6700\u70ed\u95e8\u76845\u4e2a\u9910\u5385";
        AiChatResponse ruleResponse = rankedDiningResponse();
        when(mockAiService.preview(eq(message), eq("zh-CN"))).thenReturn(ruleResponse);
        when(deepSeekAiService.isConfigured()).thenReturn(true);

        AiChatResponse response = service(mockAiService, deepSeekAiService).preview(message, "zh-CN");

        assertSame(ruleResponse, response);
        assertEquals("recommend_place", response.intent);
        verify(deepSeekAiService, never()).chat(eq(message), eq("zh-CN"));
        verify(deepSeekAiService, never()).smallTalk(eq(message), eq("zh-CN"));
    }

    @Test
    void deepSeekSmallTalkFailureFallsBackToLocalNoResultPoiResponse() {
        MockAiService mockAiService = mock(MockAiService.class);
        DeepSeekAiService deepSeekAiService = mock(DeepSeekAiService.class);
        AiChatResponse ruleResponse = noResultFindPoiResponse();
        when(mockAiService.preview(eq("南京信息工程大学附近有地铁吗"), eq("zh-CN"))).thenReturn(ruleResponse);
        when(deepSeekAiService.isConfigured()).thenReturn(true);
        when(deepSeekAiService.smallTalk(eq("南京信息工程大学附近有地铁吗"), eq("zh-CN")))
                .thenThrow(new IllegalStateException("deepseek_timeout"));

        AiChatResponse response = service(mockAiService, deepSeekAiService).preview("南京信息工程大学附近有地铁吗", "zh-CN");

        assertSame(ruleResponse, response);
        assertEquals("find_poi", response.intent);
    }

    @Test
    void deepSeekSmallTalkDoesNotOverrideMatchedPoiFallback() {
        MockAiService mockAiService = mock(MockAiService.class);
        DeepSeekAiService deepSeekAiService = mock(DeepSeekAiService.class);
        AiChatResponse fallback = fallbackPrinterResponse();
        AiChatResponse deepSeekResponse = smallTalkResponse("Unrelated small talk.");
        when(mockAiService.preview(eq("Find Campus Print Shop"), eq("en-US"))).thenReturn(fallback);
        when(deepSeekAiService.isConfigured()).thenReturn(true);
        when(deepSeekAiService.chat(eq("Find Campus Print Shop"), eq("en-US"))).thenReturn(deepSeekResponse);

        AiChatResponse response = service(mockAiService, deepSeekAiService).preview("Find Campus Print Shop", "en-US");

        assertSame(fallback, response);
        assertEquals("find_poi", response.intent);
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

    private AiChatResponse noResultFindPoiResponse() {
        AiChatResponse response = new AiChatResponse();
        response.intent = "find_poi";
        response.reply = "No related campus place was found.";
        AiChatResponse.MapAction highlight = new AiChatResponse.MapAction("highlight_pois");
        highlight.poiIds = List.of();
        response.mapActions.add(highlight);
        response.toolCalls.add(new AiChatResponse.ToolCall("searchPoi", java.util.Map.of("keyword", "NUIST")));
        return response;
    }

    private AiChatResponse rankedDiningResponse() {
        AiChatResponse response = new AiChatResponse();
        response.intent = "recommend_place";
        response.reply = "\u5df2\u6309 Place Rank \u4e3a\u4f60\u627e\u5230\u524d 5 \u4e2a\u70ed\u95e8\u9910\u5385\u3002";
        response.pois = List.of(canteen());
        AiChatResponse.MapAction highlight = new AiChatResponse.MapAction("highlight_pois");
        highlight.poiIds = List.of(9L);
        response.mapActions.add(highlight);
        AiChatResponse.MapAction open = new AiChatResponse.MapAction("open_poi_detail");
        open.poiId = 9L;
        response.mapActions.add(open);
        response.toolCalls.add(new AiChatResponse.ToolCall("searchPoiByRank", java.util.Map.of("category", "DINING", "limit", 5)));
        return response;
    }

    private AiChatResponse smallTalkResponse(String reply) {
        AiChatResponse response = new AiChatResponse();
        response.intent = "small_talk";
        response.reply = reply;
        return response;
    }

    private AiChatResponse recommendShoppingResponse() {
        AiChatResponse response = new AiChatResponse();
        response.intent = "recommend_place";
        response.reply = "已为你推荐校园超市。";
        response.pois = List.of(supermarket());
        AiChatResponse.MapAction highlight = new AiChatResponse.MapAction("highlight_pois");
        highlight.poiIds = List.of(73L);
        response.mapActions.add(highlight);
        AiChatResponse.MapAction open = new AiChatResponse.MapAction("open_poi_detail");
        open.poiId = 73L;
        response.mapActions.add(open);
        response.toolCalls.add(new AiChatResponse.ToolCall("searchPoiByTags", java.util.Map.of("tags", List.of("supermarket"))));
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

    private PoiEntity canteen() {
        PoiEntity poi = new PoiEntity();
        poi.id = 9L;
        poi.name = "Zhongyuan Old Canteen";
        poi.category = "DINING";
        poi.tags = "canteen,dining,top20";
        poi.enabled = true;
        poi.mapRank = 9;
        return poi;
    }

    private PoiEntity supermarket() {
        PoiEntity poi = new PoiEntity();
        poi.id = 73L;
        poi.name = "Campus Supermarket Central";
        poi.category = "SERVICE";
        poi.tags = "supermarket,shopping,service,daily-life";
        poi.enabled = true;
        return poi;
    }
}
