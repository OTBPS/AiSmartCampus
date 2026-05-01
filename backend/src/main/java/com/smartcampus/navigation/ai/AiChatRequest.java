package com.smartcampus.navigation.ai;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class AiChatRequest {
    @NotBlank
    public String message;
    public String locale;
    public RouteContext routeContext;

    public static class RouteContext {
        public Long originPoiId;
        public Long destinationPoiId;
        public List<Long> waypointPoiIds = new ArrayList<>();
    }
}
