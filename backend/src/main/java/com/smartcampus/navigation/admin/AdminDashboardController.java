package com.smartcampus.navigation.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcampus.navigation.ai.AiMessageEntity;
import com.smartcampus.navigation.ai.AiMessageMapper;
import com.smartcampus.navigation.common.ApiResponse;
import com.smartcampus.navigation.discover.DiscoverPostEntity;
import com.smartcampus.navigation.discover.DiscoverPostMapper;
import com.smartcampus.navigation.feedback.FeedbackEntity;
import com.smartcampus.navigation.feedback.FeedbackMapper;
import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {
    private final PoiMapper poiMapper;
    private final FeedbackMapper feedbackMapper;
    private final AiMessageMapper aiMessageMapper;
    private final DiscoverPostMapper discoverPostMapper;

    public AdminDashboardController(
            PoiMapper poiMapper,
            FeedbackMapper feedbackMapper,
            AiMessageMapper aiMessageMapper,
            DiscoverPostMapper discoverPostMapper
    ) {
        this.poiMapper = poiMapper;
        this.feedbackMapper = feedbackMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.discoverPostMapper = discoverPostMapper;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> dashboard() {
        DashboardResponse response = new DashboardResponse();
        response.poiCount = poiMapper.selectCount(new QueryWrapper<PoiEntity>());
        response.pendingFeedbackCount = feedbackMapper.selectCount(new QueryWrapper<FeedbackEntity>().eq("status", "PENDING"));
        response.aiQueryCount = aiMessageMapper.selectCount(new QueryWrapper<AiMessageEntity>());
        response.discoverPostCount = discoverPostMapper.selectCount(new QueryWrapper<DiscoverPostEntity>());
        return ApiResponse.ok(response);
    }
}

