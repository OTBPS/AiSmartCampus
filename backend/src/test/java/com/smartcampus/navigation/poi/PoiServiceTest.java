package com.smartcampus.navigation.poi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcampus.navigation.common.BizException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class PoiServiceTest {
    @TempDir
    Path tempDir;

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

    @Test
    void createRejectsDuplicateMapRankBeforeInsert() {
        PoiMapper poiMapper = mock(PoiMapper.class);
        when(poiMapper.selectCount(any())).thenReturn(1L);

        PoiRequest request = request();
        request.mapRank = 14;

        BizException ex = assertThrows(BizException.class, () -> new PoiService(poiMapper).create(request));

        assertEquals("Map Rank 已被其他地点使用，请更换或留空", ex.getMessage());
        verify(poiMapper, never()).insert(any(PoiEntity.class));
    }

    @Test
    void createAllowsBlankMapRankAndNormalizesLiteralNullSourceUrl() {
        PoiMapper poiMapper = mock(PoiMapper.class);
        PoiRequest request = request();
        request.mapRank = null;
        request.sourceUrl = "null";

        new PoiService(poiMapper).create(request);

        ArgumentCaptor<PoiEntity> captor = ArgumentCaptor.forClass(PoiEntity.class);
        verify(poiMapper).insert(captor.capture());
        assertNull(captor.getValue().mapRank);
        assertEquals("", captor.getValue().sourceUrl);
        verify(poiMapper, never()).selectCount(any());
    }

    @Test
    void updateImageStoresOneImageAndDeletesPreviousLocalImage() throws Exception {
        Path poiUploadDir = tempDir.resolve("uploads").resolve("poi");
        Files.createDirectories(poiUploadDir);
        Files.writeString(poiUploadDir.resolve("old.png"), "old");

        PoiMapper poiMapper = mock(PoiMapper.class);
        PoiEntity entity = poi();
        entity.imageUrl = "/uploads/poi/old.png";
        AtomicReference<PoiEntity> saved = new AtomicReference<>(entity);
        when(poiMapper.selectById(7L)).thenAnswer(invocation -> saved.get());
        when(poiMapper.updateById(any(PoiEntity.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return 1;
        });

        MockMultipartFile image = new MockMultipartFile("image", "cafe.png", "image/png", new byte[] {1, 2, 3});

        PoiEntity updated = new PoiService(poiMapper, poiUploadDir.toString()).updateImage(7L, image);

        assertTrue(updated.imageUrl.startsWith("/uploads/poi/poi-7-"));
        assertFalse(Files.exists(poiUploadDir.resolve("old.png")));
        assertEquals(1, Files.list(poiUploadDir).count());
    }

    @Test
    void updateImageRejectsNonImageFile() {
        PoiMapper poiMapper = mock(PoiMapper.class);
        when(poiMapper.selectById(7L)).thenReturn(poi());
        MockMultipartFile file = new MockMultipartFile("image", "note.txt", "text/plain", "bad".getBytes());

        BizException ex = assertThrows(
                BizException.class,
                () -> new PoiService(poiMapper, tempDir.resolve("uploads").resolve("poi").toString()).updateImage(7L, file)
        );

        assertEquals("地点图片只支持 JPG、PNG、WEBP 或 GIF", ex.getMessage());
        verify(poiMapper, never()).updateById(any(PoiEntity.class));
    }

    private QueryWrapper<PoiEntity> capturedWrapper(PoiMapper poiMapper) {
        ArgumentCaptor<QueryWrapper<PoiEntity>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(poiMapper).selectList(captor.capture());
        return captor.getValue();
    }

    private PoiRequest request() {
        PoiRequest request = new PoiRequest();
        request.name = "EMS";
        request.category = "SERVICE";
        request.longitude = BigDecimal.valueOf(118.717342);
        request.latitude = BigDecimal.valueOf(32.203935);
        request.locationText = "EMS Delivery Point";
        request.openStatus = "OPEN";
        request.tags = "EMS, Delivery Service";
        request.remark = "EMS Delivery Point";
        request.enabled = true;
        return request;
    }

    private PoiEntity poi() {
        PoiEntity poi = new PoiEntity();
        poi.id = 7L;
        poi.name = "Campus Cafe";
        poi.category = "DINING";
        poi.longitude = BigDecimal.valueOf(118.723975);
        poi.latitude = BigDecimal.valueOf(32.205535);
        poi.locationText = "Campus cafe";
        poi.openStatus = "OPEN";
        poi.tags = "cafe";
        poi.remark = "";
        poi.enabled = true;
        poi.sourceUrl = "";
        poi.imageUrl = "";
        return poi;
    }
}
