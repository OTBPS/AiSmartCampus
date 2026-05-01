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
