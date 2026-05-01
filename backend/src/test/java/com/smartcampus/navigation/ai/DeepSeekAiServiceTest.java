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
        PoiEntity poi = new PoiEntity();
        poi.id = 9L;
        poi.name = "Campus Print Shop";
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
