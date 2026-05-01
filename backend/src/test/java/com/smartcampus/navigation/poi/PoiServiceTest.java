package com.smartcampus.navigation.poi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PoiServiceTest {
    @Test
    void mapOnlyListReturnsRankedPoisWithLimit() {
        PoiMapper poiMapper = mock(PoiMapper.class);
        when(poiMapper.selectList(any())).thenReturn(List.of());

        new PoiService(poiMapper).list(null, null, null, true, true, 20);

        QueryWrapper<PoiEntity> wrapper = capturedWrapper(poiMapper);
        String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("enabled"));
        assertTrue(sql.contains("map_rank is not null"));
        assertTrue(sql.contains("order by map_rank asc"));
        assertTrue(sql.contains("limit 20"));
    }

    @Test
    void defaultListStillReturnsAllEnabledPoisWithoutMapOnlyFilter() {
        PoiMapper poiMapper = mock(PoiMapper.class);
        when(poiMapper.selectList(any())).thenReturn(List.of());

        new PoiService(poiMapper).list(null, null, null, true);

        QueryWrapper<PoiEntity> wrapper = capturedWrapper(poiMapper);
        String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("enabled"));
        assertTrue(sql.contains("order by category asc"));
        assertFalse(sql.contains("map_rank is not null"));
        assertFalse(sql.contains("limit"));
    }

    private QueryWrapper<PoiEntity> capturedWrapper(PoiMapper poiMapper) {
        ArgumentCaptor<QueryWrapper<PoiEntity>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(poiMapper).selectList(captor.capture());
        return captor.getValue();
    }
}
