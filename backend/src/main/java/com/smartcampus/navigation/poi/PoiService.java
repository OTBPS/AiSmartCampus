package com.smartcampus.navigation.poi;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcampus.navigation.common.BizException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PoiService {
    private final PoiMapper poiMapper;

    public PoiService(PoiMapper poiMapper) {
        this.poiMapper = poiMapper;
    }

    public List<PoiEntity> list(String keyword, String category, String tag, Boolean enabledOnly) {
        QueryWrapper<PoiEntity> wrapper = new QueryWrapper<>();
        if (Boolean.TRUE.equals(enabledOnly)) {
            wrapper.eq("enabled", true);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq("category", category);
        }
        if (StringUtils.hasText(tag)) {
            wrapper.like("tags", tag);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("name", keyword).or().like("location_text", keyword).or().like("tags", keyword));
        }
        wrapper.orderByAsc("category").orderByDesc("updated_at");
        return poiMapper.selectList(wrapper);
    }

    public PoiEntity get(Long id) {
        PoiEntity poi = poiMapper.selectById(id);
        if (poi == null) {
            throw new BizException("地点不存在");
        }
        return poi;
    }

    public PoiEntity create(PoiRequest request) {
        PoiEntity entity = new PoiEntity();
        apply(entity, request);
        poiMapper.insert(entity);
        return entity;
    }

    public PoiEntity update(Long id, PoiRequest request) {
        PoiEntity entity = get(id);
        apply(entity, request);
        poiMapper.updateById(entity);
        return get(id);
    }

    public PoiEntity updateStatus(Long id, String status, Boolean enabled) {
        PoiEntity entity = get(id);
        if (StringUtils.hasText(status)) {
            entity.openStatus = status;
        }
        if (enabled != null) {
            entity.enabled = enabled;
        }
        poiMapper.updateById(entity);
        return get(id);
    }

    public PoiEntity updateStatusAndRemark(Long id, String status, String remark, Boolean enabled) {
        PoiEntity entity = get(id);
        if (StringUtils.hasText(status)) {
            entity.openStatus = status;
        }
        if (StringUtils.hasText(remark)) {
            entity.remark = remark;
        }
        if (enabled != null) {
            entity.enabled = enabled;
        }
        poiMapper.updateById(entity);
        return get(id);
    }

    private void apply(PoiEntity entity, PoiRequest request) {
        entity.name = request.name;
        entity.category = request.category;
        entity.longitude = request.longitude;
        entity.latitude = request.latitude;
        entity.locationText = request.locationText;
        entity.openStatus = StringUtils.hasText(request.openStatus) ? request.openStatus : "OPEN";
        entity.tags = request.tags == null ? "" : request.tags;
        entity.sheltered = Boolean.TRUE.equals(request.sheltered);
        entity.remark = request.remark == null ? "" : request.remark;
        entity.enabled = request.enabled == null || request.enabled;
    }
}
