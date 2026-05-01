package com.smartcampus.navigation.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartcampus.navigation.common.BizException;
import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FeedbackServiceTest {
    @Test
    void submitRejectsMissingPoiBinding() {
        FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
        PoiService poiService = mock(PoiService.class);
        FeedbackRequest request = request(null);

        BizException ex = assertThrows(
                BizException.class,
                () -> new FeedbackService(feedbackMapper, poiService).submit(1L, request)
        );

        assertEquals("反馈必须绑定 POI", ex.getMessage());
        verifyNoInteractions(feedbackMapper);
        verifyNoInteractions(poiService);
    }

    @Test
    void submitRejectsUnknownPoiBeforeInsert() {
        FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
        PoiService poiService = mock(PoiService.class);
        when(poiService.get(404L)).thenThrow(new BizException("地点不存在"));

        BizException ex = assertThrows(
                BizException.class,
                () -> new FeedbackService(feedbackMapper, poiService).submit(1L, request(404L))
        );

        assertEquals("地点不存在", ex.getMessage());
        verify(poiService).get(404L);
        verify(feedbackMapper, never()).insert(any(FeedbackEntity.class));
    }

    @Test
    void submitStoresFeedbackAfterPoiBindingIsValidated() {
        FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
        PoiService poiService = mock(PoiService.class);
        PoiEntity poi = new PoiEntity();
        poi.id = 9L;
        when(poiService.get(9L)).thenReturn(poi);
        when(feedbackMapper.insert(any(FeedbackEntity.class))).thenReturn(1);

        FeedbackEntity entity = new FeedbackService(feedbackMapper, poiService).submit(1L, request(9L));

        assertEquals(1L, entity.userId);
        assertEquals(9L, entity.poiId);
        assertEquals("PENDING", entity.status);
        assertEquals("", entity.reviewNote);
        verify(poiService).get(9L);
        verify(feedbackMapper).insert(argThat((FeedbackEntity saved) ->
                saved.poiId.equals(9L)
                        && "INFO_ERROR".equals(saved.type)
                        && "Campus Print Shop also opens on weekend afternoons.".equals(saved.content)
                        && "PENDING".equals(saved.status)
        ));
    }

    @Test
    void approveFeedbackStillSyncsVisiblePoiRemark() {
        FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
        PoiService poiService = mock(PoiService.class);
        FeedbackEntity pending = new FeedbackEntity();
        pending.id = 3L;
        pending.userId = 1L;
        pending.poiId = 9L;
        pending.status = "PENDING";
        when(feedbackMapper.selectById(3L)).thenReturn(pending);
        when(feedbackMapper.updateById(any(FeedbackEntity.class))).thenReturn(1);

        FeedbackReviewRequest request = new FeedbackReviewRequest();
        request.status = "APPROVED";
        request.reviewNote = "Verified";
        request.poiRemark = "Added based on user feedback.";

        FeedbackEntity reviewed = new FeedbackService(feedbackMapper, poiService).review(3L, request);

        assertSame(pending, reviewed);
        assertEquals("APPROVED", pending.status);
        assertEquals("Verified", pending.reviewNote);
        verify(poiService).updateFromFeedbackReview(9L, null, "Added based on user feedback.", null, null, null);
    }

    @Test
    void approveFeedbackCanSyncPoiCoordinates() {
        FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
        PoiService poiService = mock(PoiService.class);
        FeedbackEntity pending = new FeedbackEntity();
        pending.id = 4L;
        pending.userId = 1L;
        pending.poiId = 12L;
        pending.status = "PENDING";
        when(feedbackMapper.selectById(4L)).thenReturn(pending);
        when(feedbackMapper.updateById(any(FeedbackEntity.class))).thenReturn(1);

        FeedbackReviewRequest request = new FeedbackReviewRequest();
        request.status = "APPROVED";
        request.reviewNote = "Coordinate verified";
        request.poiLongitude = BigDecimal.valueOf(118.719001);
        request.poiLatitude = BigDecimal.valueOf(32.204001);

        FeedbackEntity reviewed = new FeedbackService(feedbackMapper, poiService).review(4L, request);

        assertSame(pending, reviewed);
        verify(poiService).updateFromFeedbackReview(
                12L,
                null,
                null,
                null,
                BigDecimal.valueOf(118.719001),
                BigDecimal.valueOf(32.204001)
        );
    }

    private FeedbackRequest request(Long poiId) {
        FeedbackRequest request = new FeedbackRequest();
        request.poiId = poiId;
        request.type = "INFO_ERROR";
        request.content = "Campus Print Shop also opens on weekend afternoons.";
        return request;
    }
}
