package com.smartcampus.navigation.discover;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcampus.navigation.common.BizException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DiscoverService {
    private final DiscoverPostMapper mapper;

    public DiscoverService(DiscoverPostMapper mapper) {
        this.mapper = mapper;
    }

    public List<DiscoverPostEntity> listPublished() {
        return mapper.selectList(new QueryWrapper<DiscoverPostEntity>().eq("status", "PUBLISHED").orderByDesc("created_at"));
    }

    public List<DiscoverPostEntity> adminList() {
        return mapper.selectList(new QueryWrapper<DiscoverPostEntity>().orderByDesc("created_at"));
    }

    public DiscoverPostEntity create(DiscoverPostRequest request) {
        DiscoverPostEntity entity = new DiscoverPostEntity();
        apply(entity, request);
        mapper.insert(entity);
        return entity;
    }

    public DiscoverPostEntity update(Long id, DiscoverPostRequest request) {
        DiscoverPostEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BizException("发现内容不存在");
        }
        apply(entity, request);
        mapper.updateById(entity);
        return mapper.selectById(id);
    }

    private void apply(DiscoverPostEntity entity, DiscoverPostRequest request) {
        entity.title = request.title;
        entity.summary = request.summary;
        entity.poiId = request.poiId;
        entity.category = request.category;
        entity.coverUrl = request.coverUrl == null ? "" : request.coverUrl;
        entity.status = request.status == null ? "PUBLISHED" : request.status;
    }
}

