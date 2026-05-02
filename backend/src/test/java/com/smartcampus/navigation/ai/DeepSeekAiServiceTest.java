package com.smartcampus.navigation.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiService;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeepSeekAiServiceTest {
    @Test
    void parserRejectsNonJsonContent() {
        DeepSeekAiService service = parserService();

        assertThrows(
                IllegalStateException.class,
                () -> service.parseStructuredResponse("not json", "en-US", List.of(printer()))
        );
    }

    @Test
    void parserDropsIllegalPoiIdsAndLeavesNoMapActionsForFallback() {
        DeepSeekAiService service = parserService();
        String content = """
                {
                  "intent": "find_poi",
                  "reply": "I found a place.",
                  "poiIds": [999],
                  "toolCalls": [],
                  "mapActions": [
                    { "type": "highlight_pois", "poiIds": [999] },
                    { "type": "open_poi_detail", "poiId": 999 }
                  ]
                }
                """;

        AiChatResponse response = service.parseStructuredResponse(content, "en-US", List.of(printer()));

        assertTrue(response.pois.isEmpty());
        assertTrue(response.mapActions.isEmpty());
        assertEquals("find_poi", response.intent);
    }

    @Test
    void parserDoesNotInventMapActionsWhenRequiredFieldIsMissing() {
        DeepSeekAiService service = parserService();
        String content = """
                {
                  "intent": "find_poi",
                  "reply": "I found Campus Print Shop.",
                  "poiIds": [9],
                  "toolCalls": []
                }
                """;

        AiChatResponse response = service.parseStructuredResponse(content, "en-US", List.of(printer()));

        assertEquals(List.of(printer().id), response.pois.stream().map(poi -> poi.id).toList());
        assertTrue(response.mapActions.isEmpty());
    }

    @Test
    void parserAcceptsFindNoteIntentWithoutMapActions() {
        DeepSeekAiService service = parserService();
        String content = """
                {
                  "intent": "find_note",
                  "reply": "I will search related notes.",
                  "poiIds": [],
                  "toolCalls": [],
                  "mapActions": []
                }
                """;

        AiChatResponse response = service.parseStructuredResponse(content, "en-US", List.of(printer()));

        assertEquals("find_note", response.intent);
        assertTrue(response.pois.isEmpty());
        assertTrue(response.toolCalls.isEmpty());
        assertTrue(response.mapActions.isEmpty());
    }

    @Test
    void parserKeepsOrderedMultiPointRouteAndDropsIllegalIds() {
        DeepSeekAiService service = parserService();
        PoiEntity dorm = poi(7L, "Xiyuan Dormitory Area");
        PoiEntity canteen = poi(10L, "Central Campus New Canteen");
        PoiEntity library = poi(1L, "NUIST Library Study Area");
        String content = """
                {
                  "intent": "route_help",
                  "reply": "I prepared the route.",
                  "poiIds": [7, 10, 1],
                  "toolCalls": [],
                  "mapActions": [
                    {
                      "type": "draw_route",
                      "poiIds": [7, 999, 10, 1],
                      "routeMode": "AMAP_FALLBACK",
                      "payload": {
                        "from": "Xiyuan Dormitory Area",
                        "to": "NUIST Library Study Area",
                        "via": ["Central Campus New Canteen"],
                        "source": "text"
                      }
                    }
                  ]
                }
                """;

        AiChatResponse response = service.parseStructuredResponse(content, "en-US", List.of(dorm, canteen, library));

        assertEquals("route_help", response.intent);
        assertEquals(List.of(7L, 10L, 1L), response.mapActions.get(0).poiIds);
        assertEquals(List.of(7L, 10L, 1L), response.pois.stream().map(poi -> poi.id).toList());
    }

    private DeepSeekAiService parserService() {
        return new DeepSeekAiService(
                mock(PoiService.class),
                new ObjectMapper(),
                "test-key",
                "https://api.deepseek.com",
                "deepseek-chat",
                1
        );
    }

    private PoiEntity printer() {
        return poi(9L, "Campus Print Shop");
    }

    private PoiEntity poi(Long id, String name) {
        PoiEntity poi = new PoiEntity();
        poi.id = id;
        poi.name = name;
        poi.category = "SERVICE";
        poi.locationText = "Campus service point near the central canteen area";
        poi.openStatus = "OPEN";
        poi.tags = "print,copy,binding,service,sheltered";
        poi.sheltered = true;
        poi.remark = "Supports printing, copying, and binding for course materials.";
        poi.enabled = true;
        return poi;
    }
}
