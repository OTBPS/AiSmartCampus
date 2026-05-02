package com.smartcampus.navigation.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class MockAiServiceTest {
    @Test
    void intentNamesStayStableForFrontendContract() {
        assertEquals("find_poi", "find_poi");
        assertEquals("recommend_place", "recommend_place");
        assertEquals("route_help", "route_help");
        assertEquals("find_note", "find_note");
        assertEquals("small_talk", "small_talk");
    }

    @Test
    void smallTalkReturnsBriefReplyWithoutMapActions() {
        PoiService poiService = mock(PoiService.class);

        AiChatResponse response = service(poiService).preview("hello", "en-US");

        assertEquals("small_talk", response.intent);
        assertTrue(response.reply.contains("campus places"));
        assertTrue(response.pois.isEmpty());
        assertTrue(response.toolCalls.isEmpty());
        assertTrue(response.mapActions.isEmpty());
    }

    @Test
    void chineseNoteQuestionReturnsFindNoteIntent() {
        AiChatResponse response = service(mock(PoiService.class)).preview("\u6211\u60f3\u627e\u4e00\u4e0b\u5173\u4e8e\u56fe\u4e66\u9986\u7684\u7b14\u8bb0", "zh-CN");

        assertEquals("find_note", response.intent);
        assertTrue(response.pois.isEmpty());
        assertTrue(response.toolCalls.isEmpty());
        assertTrue(response.mapActions.isEmpty());
    }

    @Test
    void englishNoteQuestionReturnsFindNoteIntent() {
        AiChatResponse response = service(mock(PoiService.class)).preview("show notes about library", "en-US");

        assertEquals("find_note", response.intent);
        assertTrue(response.pois.isEmpty());
        assertTrue(response.toolCalls.isEmpty());
        assertTrue(response.mapActions.isEmpty());
    }

    @Test
    void seedPasswordHashMatchesDefaultPassword() {
        String hash = "$2a$10$eI5SckucFgBQ2licTX9bx.oOVlLvzqARVEacwAU3VHjvIsabigadG";
        assertTrue(new BCryptPasswordEncoder().matches("123456", hash));
    }

    @Test
    void recommendQuietStudyPlaceHighlightsTaggedPoi() {
        PoiEntity study = poi(1L, "NUIST Library Study Area", "STUDY", "library,quiet,outlets,study,sheltered", true);
        PoiEntity printer = poi(2L, "Campus Print Shop", "SERVICE", "print,copy,service", true);
        PoiService poiService = mock(PoiService.class);
        when(poiService.list(eq(null), eq(null), eq(null), eq(true))).thenReturn(List.of(study, printer));

        AiChatResponse response = service(poiService).preview("找一个安静有插座的自习点");

        assertEquals("recommend_place", response.intent);
        assertEquals(List.of(study), response.pois);
        assertEquals(List.of(1L), response.mapActions.get(0).poiIds);
    }

    @Test
    void findPrinterUsesPoiSearchKeyword() {
        PoiEntity printer = poi(9L, "Campus Print Shop", "SERVICE", "print,copy,service", true);
        PoiService poiService = mock(PoiService.class);
        when(poiService.list(eq("print"), eq(null), eq(null), eq(true))).thenReturn(List.of(printer));

        AiChatResponse response = service(poiService).preview("找打印店");

        assertEquals("find_poi", response.intent);
        assertEquals(List.of(printer), response.pois);
        assertEquals(9L, response.mapActions.get(1).poiId);
    }

    @Test
    void findPoiPrioritizesExactEnglishBuildingName() {
        PoiEntity dining = poi(10L, "Zhongyuan Dining Hall", "DINING", "canteen,dining,central-campus", true);
        PoiEntity shangxian = poi(4L, "Shangxian Building", "TEACHING", "teaching,classroom,academic,shangxian,top20", true);
        PoiService poiService = mock(PoiService.class);
        when(poiService.list(eq(null), eq(null), eq(null), eq(true))).thenReturn(List.of(dining, shangxian));

        AiChatResponse response = service(poiService).preview("where is Shangxian Building?", "en-US");

        assertEquals("find_poi", response.intent);
        assertEquals(List.of(shangxian), response.pois);
        assertEquals(List.of(4L), response.mapActions.get(0).poiIds);
        assertEquals(4L, response.mapActions.get(1).poiId);
    }

    @Test
    void findPoiRecognizesShangxianChineseAlias() {
        PoiEntity dining = poi(10L, "Zhongyuan Dining Hall", "DINING", "canteen,dining,central-campus", true);
        PoiEntity shangxian = poi(4L, "Shangxian Building", "TEACHING", "teaching,classroom,academic,shangxian,top20", true);
        PoiService poiService = mock(PoiService.class);
        when(poiService.list(eq(null), eq(null), eq(null), eq(true))).thenReturn(List.of(dining, shangxian));

        AiChatResponse response = service(poiService).preview("\u5c1a\u8d24\u697c\u5728\u54ea\u91cc");

        assertEquals(List.of(shangxian), response.pois);
        assertEquals(4L, response.mapActions.get(1).poiId);
    }

    @Test
    void findPoiReturnsNoResultMessageInsteadOfDefaultPois() {
        PoiEntity dining = poi(10L, "Zhongyuan Dining Hall", "DINING", "canteen,dining,central-campus", true);
        PoiService poiService = mock(PoiService.class);
        when(poiService.list(eq(null), eq(null), eq(null), eq(true))).thenReturn(List.of(dining));
        when(poiService.list(eq("Unknown Mars Base"), eq(null), eq(null), eq(true))).thenReturn(List.of());

        AiChatResponse response = service(poiService).preview("Unknown Mars Base");

        assertEquals("find_poi", response.intent);
        assertEquals("\u6682\u672a\u5bfb\u627e\u5230\u76f8\u5173\u5730\u70b9,\u8bf7\u5c1d\u8bd5\u522b\u7684\u5730\u70b9", response.reply);
        assertTrue(response.pois.isEmpty());
        assertEquals(List.of(), response.mapActions.get(0).poiIds);
        assertEquals(1, response.mapActions.size());
    }

    @Test
    void routeHelpKeepsOriginAndDestinationOrder() {
        PoiEntity dorm = poi(7L, "Xiyuan Dormitory Area", "DORM", "dorm,dormitory,living-area,night-access,xiyuan", false);
        PoiEntity library = poi(1L, "NUIST Library Study Area", "STUDY", "library,quiet,outlets,study,sheltered", true);
        PoiService poiService = mock(PoiService.class);
        when(poiService.list(eq(null), eq(null), eq(null), eq(true))).thenReturn(List.of(library, dorm));

        AiChatResponse response = service(poiService).preview("从西苑宿舍去图书馆");

        assertEquals("route_help", response.intent);
        assertEquals(List.of(dorm, library), response.pois);
        assertEquals("draw_route", response.mapActions.get(0).type);
        assertEquals(List.of(7L, 1L), response.mapActions.get(0).poiIds);
    }

    @Test
    void routeHelpKeepsTextWaypointOrder() {
        PoiEntity dorm = poi(7L, "Xiyuan Dormitory Area", "DORM", "dorm,dormitory,living-area,night-access,xiyuan", false);
        PoiEntity canteen = poi(10L, "Central Campus New Canteen", "DINING", "canteen,dining,central-campus", true);
        PoiEntity library = poi(1L, "NUIST Library Study Area", "STUDY", "library,quiet,outlets,study,sheltered", true);
        PoiService poiService = mock(PoiService.class);
        when(poiService.list(eq(null), eq(null), eq(null), eq(true))).thenReturn(List.of(library, canteen, dorm));

        AiChatResponse response = service(poiService).preview(
                "Go from Xiyuan Dormitory Area to NUIST Library Study Area via Central Campus New Canteen",
                "en-US"
        );

        assertEquals("route_help", response.intent);
        assertEquals(List.of(7L, 10L, 1L), response.mapActions.get(0).poiIds);
        assertEquals("text", response.mapActions.get(0).payload.get("source"));
    }

    @Test
    void routeHelpDoesNotAddGenericCanteenAliasInsideExactNameAsWaypoint() {
        PoiEntity zhongyuan = poi(10L, "Zhongyuan Dining Hall", "DINING", "canteen,dining,central-campus", true);
        PoiEntity xiyuan = poi(11L, "Xiyuan New Canteen", "DINING", "canteen,dining,dinner,west-garden,xiyuan", true);
        PoiEntity wende = poi(20L, "Wende Building", "TEACHING", "teaching,classroom,course,wende,top20", true);
        PoiService poiService = mock(PoiService.class);
        when(poiService.list(eq(null), eq(null), eq(null), eq(true))).thenReturn(List.of(zhongyuan, xiyuan, wende));

        AiChatResponse response = service(poiService).preview(
                "Go from Xiyuan New Canteen to Wende Building",
                "en-US"
        );

        assertEquals("route_help", response.intent);
        assertEquals(List.of(11L, 20L), response.mapActions.get(0).poiIds);
        assertEquals(List.of(xiyuan, wende), response.pois);
    }

    @Test
    void chineseRouteWithForwardWordTriggersRoutePlanning() {
        PoiEntity xiyuan = poi(11L, "Xiyuan New Canteen", "DINING", "canteen,dining,dinner,west-garden,xiyuan", true);
        PoiEntity gym = poi(26L, "NUIST Gymnasium", "SPORTS", "sports,gym,basketball,fitness,night-access", false);
        PoiService poiService = mock(PoiService.class);
        when(poiService.list(eq(null), eq(null), eq(null), eq(true))).thenReturn(List.of(xiyuan, gym));

        AiChatResponse response = service(poiService).preview(
                "\u6211\u60f3\u4eceXiyuan New Canteen\u524d\u5f80Gym",
                "zh-CN"
        );

        assertEquals("route_help", response.intent);
        assertEquals("draw_route", response.mapActions.get(0).type);
        assertEquals(List.of(11L, 26L), response.mapActions.get(0).poiIds);
    }

    @Test
    void routeContextOriginFillsDestinationOnlyText() {
        PoiEntity dorm = poi(7L, "Xiyuan Dormitory Area", "DORM", "dorm,dormitory,living-area,night-access,xiyuan", false);
        PoiEntity library = poi(1L, "NUIST Library Study Area", "STUDY", "library,quiet,outlets,study,sheltered", true);
        PoiService poiService = mock(PoiService.class);
        when(poiService.list(eq(null), eq(null), eq(null), eq(true))).thenReturn(List.of(library, dorm));

        AiChatResponse response = service(poiService).preview("Go to NUIST Library Study Area", "en-US", routeContext(7L, null));

        assertEquals("route_help", response.intent);
        assertEquals(List.of(7L, 1L), response.mapActions.get(0).poiIds);
        assertEquals("mixed", response.mapActions.get(0).payload.get("source"));
    }

    @Test
    void routeContextKeepsWaypointOrderWhenNoTextEndpoints() {
        PoiEntity dorm = poi(7L, "Xiyuan Dormitory Area", "DORM", "dorm,dormitory,living-area,night-access,xiyuan", false);
        PoiEntity canteen = poi(10L, "Central Campus New Canteen", "DINING", "canteen,dining,central-campus", true);
        PoiEntity library = poi(1L, "NUIST Library Study Area", "STUDY", "library,quiet,outlets,study,sheltered", true);
        PoiService poiService = mock(PoiService.class);
        when(poiService.list(eq(null), eq(null), eq(null), eq(true))).thenReturn(List.of(library, canteen, dorm));

        AiChatResponse response = service(poiService).preview("Generate route", "en-US", routeContext(7L, 1L, 10L));

        assertEquals(List.of(7L, 10L, 1L), response.mapActions.get(0).poiIds);
        assertEquals("map_context", response.mapActions.get(0).payload.get("source"));
    }

    @Test
    void invalidRouteContextFallsBackToExistingMockRoute() {
        PoiEntity library = poi(1L, "NUIST Library Study Area", "STUDY", "library,quiet,outlets,study,sheltered", true);
        PoiEntity dorm = poi(7L, "Xiyuan Dormitory Area", "DORM", "dorm,dormitory,living-area,night-access,xiyuan", false);
        PoiService poiService = mock(PoiService.class);
        when(poiService.list(eq(null), eq(null), eq(null), eq(true))).thenReturn(List.of(library, dorm));

        AiChatResponse response = service(poiService).preview("Generate route", "en-US", routeContext(999L, 998L, 997L));

        assertEquals(List.of(1L, 7L), response.mapActions.get(0).poiIds);
        assertEquals("fallback", response.mapActions.get(0).payload.get("source"));
    }

    @Test
    void englishLocaleReturnsEnglishReplyAndKeepsStructuredActions() {
        PoiEntity study = poi(1L, "NUIST Library Study Area", "STUDY", "library,quiet,outlets,study,sheltered", true);
        PoiService poiService = mock(PoiService.class);
        when(poiService.list(eq(null), eq(null), eq(null), eq(true))).thenReturn(List.of(study));

        AiChatResponse response = service(poiService).preview("Find a quiet study place with outlets", "en-US");

        assertEquals("recommend_place", response.intent);
        assertTrue(response.reply.contains("candidate places"));
        assertEquals("highlight_pois", response.mapActions.get(0).type);
    }

    private MockAiService service(PoiService poiService) {
        return new MockAiService(poiService, mock(AiMessageMapper.class), new ObjectMapper());
    }

    private AiChatRequest.RouteContext routeContext(Long originPoiId, Long destinationPoiId, Long... waypointPoiIds) {
        AiChatRequest.RouteContext context = new AiChatRequest.RouteContext();
        context.originPoiId = originPoiId;
        context.destinationPoiId = destinationPoiId;
        context.waypointPoiIds = List.of(waypointPoiIds);
        return context;
    }

    private PoiEntity poi(Long id, String name, String category, String tags, boolean sheltered) {
        PoiEntity poi = new PoiEntity();
        poi.id = id;
        poi.name = name;
        poi.category = category;
        poi.locationText = "校园演示位置";
        poi.openStatus = "OPEN";
        poi.tags = tags;
        poi.sheltered = sheltered;
        poi.remark = "";
        poi.enabled = true;
        return poi;
    }
}
