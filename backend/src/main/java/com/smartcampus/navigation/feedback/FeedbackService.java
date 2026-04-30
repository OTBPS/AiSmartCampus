package com.smartcampus.navigation.feedback;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcampus.navigation.common.BizException;
import com.smartcampus.navigation.poi.PoiService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FeedbackService {
    private final FeedbackMapper feedbackMapper;
    private final PoiService poiService;

    public FeedbackService(FeedbackMapper feedbackMapper, PoiService poiService) {
        this.feedbackMapper = feedbackMapper;
        this.poiService = poiService;
    }

    public FeedbackEntity submit(Long userId, FeedbackRequest request) {
        FeedbackEntity entity = new FeedbackEntity();
        entity.userId = userId;
        entity.poiId = request.poiId;
        entity.type = request.type;
        entity.content = request.content;
        entity.status = "PENDING";
        entity.reviewNote = "";
        feedbackMapper.insert(entity);
        return entity;
    }

    public List<FeedbackEntity> mine(Long userId) {
        return feedbackMapper.selectList(new QueryWrapper<FeedbackEntity>().eq("user_id", userId).orderByDesc("created_at"));
    }

    public List<FeedbackEntity> adminList(String status) {
        QueryWrapper<FeedbackEntity> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        return feedbackMapper.selectList(wrapper);
    }

    public FeedbackEntity review(Long id, FeedbackReviewRequest request) {
        FeedbackEntity entity = feedbackMapper.selectById(id);
        if (entity == null) {
            throw new BizException("反馈不存在");
        }
        entity.status = request.status;
        entity.reviewNote = request.reviewNote == null ? "" : request.reviewNote;
        entity.reviewedAt = LocalDateTime.now();
        feedbackMapper.updateById(entity);
        if ("APPROVED".equals(request.status) && entity.poiId != null
                && (StringUtils.hasText(request.poiOpenStatus) || StringUtils.hasText(request.poiRemark))) {
            poiService.updateStatusAndRemark(entity.poiId, request.poiOpenStatus, request.poiRemark, null);
        }
        return feedbackMapper.selectById(id);
    }
}
