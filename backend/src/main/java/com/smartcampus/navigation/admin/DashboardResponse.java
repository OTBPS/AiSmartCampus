package com.smartcampus.navigation.admin;

import java.util.ArrayList;
import java.util.List;

public class DashboardResponse {
    public long poiCount;
    public long pendingFeedbackCount;
    public long aiQueryCount;
    public long discoverPostCount;
    public List<StatItem> aiIntentStats = new ArrayList<>();
    public List<StatItem> mapActionStats = new ArrayList<>();
    public List<StatItem> feedbackStatusStats = new ArrayList<>();
    public List<HotPoi> hotPois = new ArrayList<>();

    public static class StatItem {
        public String key;
        public String label;
        public long count;
        public int percent;

        public StatItem(String key, String label, long count, int percent) {
            this.key = key;
            this.label = label;
            this.count = count;
            this.percent = percent;
        }
    }

    public static class HotPoi {
        public Long id;
        public String name;
        public String category;
        public String openStatus;
        public long heat;

        public HotPoi(Long id, String name, String category, String openStatus, long heat) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.openStatus = openStatus;
            this.heat = heat;
        }
    }
}
