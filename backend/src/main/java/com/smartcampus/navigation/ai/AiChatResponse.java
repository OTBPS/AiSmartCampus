package com.smartcampus.navigation.ai;

import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.discover.DiscoverPostResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AiChatResponse {
    public String intent;
    public String reply;
    public List<ToolCall> toolCalls = new ArrayList<>();
    public List<MapAction> mapActions = new ArrayList<>();
    public List<PoiEntity> pois = new ArrayList<>();
    public List<DiscoverPostResponse> notes = new ArrayList<>();

    public static class ToolCall {
        public String tool;
        public Map<String, Object> arguments;

        public ToolCall() {
        }

        public ToolCall(String tool, Map<String, Object> arguments) {
            this.tool = tool;
            this.arguments = arguments;
        }
    }

    public static class MapAction {
        public String type;
        public List<Long> poiIds = new ArrayList<>();
        public Long poiId;
        public String routeMode;
        public Map<String, Object> payload;

        public MapAction() {
        }

        public MapAction(String type) {
            this.type = type;
        }
    }
}
