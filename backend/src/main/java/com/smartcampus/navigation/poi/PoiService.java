package com.smartcampus.navigation.poi;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcampus.navigation.common.BizException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PoiService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final PoiMapper poiMapper;
    private final String uploadDir;

    public PoiService(PoiMapper poiMapper) {
        this(poiMapper, "uploads/poi");
    }

    @Autowired
    public PoiService(PoiMapper poiMapper, @Value("${app.upload.poi-dir:uploads/poi}") String uploadDir) {
        this.poiMapper = poiMapper;
        this.uploadDir = uploadDir;
    }

    public List<PoiEntity> list(String keyword, String category, String tag, Boolean enabledOnly) {
        return list(keyword, category, tag, enabledOnly, false, null);
    }

    public List<PoiEntity> list(String keyword, String category, String tag, Boolean enabledOnly, Boolean mapOnly, Integer limit) {
        QueryWrapper<PoiEntity> wrapper = new QueryWrapper<>();
        if (Boolean.TRUE.equals(enabledOnly)) {
            wrapper.eq("enabled", true);
        }
        if (Boolean.TRUE.equals(mapOnly)) {
            wrapper.isNotNull("map_rank");
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
        if (Boolean.TRUE.equals(mapOnly)) {
            wrapper.orderByAsc("map_rank");
        } else {
            wrapper.orderByAsc("category").orderByDesc("updated_at");
        }
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + Math.min(limit, 100));
        }
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
        validateForSave(request, null);
        apply(entity, request);
        poiMapper.insert(entity);
        return entity;
    }

    public PoiEntity update(Long id, PoiRequest request) {
        PoiEntity entity = get(id);
        validateForSave(request, id);
        apply(entity, request);
        poiMapper.updateById(entity);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        PoiEntity entity = get(id);
        int deleted = poiMapper.deleteById(id);
        if (deleted > 0) {
            deleteLocalImageIfManaged(entity.imageUrl);
        }
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

    public PoiEntity updateImage(Long id, MultipartFile image) {
        PoiEntity entity = get(id);
        String oldImageUrl = entity.imageUrl;
        String imageUrl = storePoiImage(id, image);
        entity.imageUrl = imageUrl;
        poiMapper.updateById(entity);
        deleteLocalImageIfManaged(oldImageUrl);
        return get(id);
    }

    public PoiEntity deleteImage(Long id) {
        PoiEntity entity = get(id);
        String oldImageUrl = entity.imageUrl;
        entity.imageUrl = "";
        poiMapper.updateById(entity);
        deleteLocalImageIfManaged(oldImageUrl);
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

    public PoiEntity updateFromFeedbackReview(Long id, String status, String remark, Boolean enabled, BigDecimal longitude, BigDecimal latitude) {
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
        if (longitude != null || latitude != null) {
            if (longitude == null || latitude == null) {
                throw new BizException("POI coordinate update requires both longitude and latitude");
            }
            validateCoordinate(longitude, latitude);
            entity.longitude = longitude;
            entity.latitude = latitude;
        }
        poiMapper.updateById(entity);
        return get(id);
    }

    private void apply(PoiEntity entity, PoiRequest request) {
        entity.name = request.name.trim();
        entity.category = request.category.trim();
        entity.longitude = request.longitude;
        entity.latitude = request.latitude;
        entity.locationText = request.locationText.trim();
        entity.openStatus = StringUtils.hasText(request.openStatus) ? request.openStatus : "OPEN";
        entity.tags = normalizeOptionalText(request.tags);
        entity.sheltered = Boolean.TRUE.equals(request.sheltered);
        entity.remark = normalizeOptionalText(request.remark);
        entity.enabled = request.enabled == null || request.enabled;
        entity.mapRank = request.mapRank;
        entity.sourceUrl = normalizeOptionalText(request.sourceUrl);
        if (request.imageUrl != null || entity.id == null) {
            entity.imageUrl = normalizeOptionalText(request.imageUrl);
        }
        if (entity.imageUrl == null) {
            entity.imageUrl = "";
        }
    }

    private void validateForSave(PoiRequest request, Long currentId) {
        if (!StringUtils.hasText(request.name)) {
            throw new BizException("POI name is required");
        }
        if (!StringUtils.hasText(request.category)) {
            throw new BizException("POI category is required");
        }
        if (!StringUtils.hasText(request.locationText)) {
            throw new BizException("POI location description is required");
        }
        if (request.longitude == null || request.latitude == null) {
            throw new BizException("POI coordinate is required");
        }
        validateCoordinate(request.longitude, request.latitude);
        if (request.mapRank != null) {
            if (request.mapRank < 1 || request.mapRank > 20) {
                throw new BizException("Map Rank 只能是 1-20，普通地点请留空");
            }
            QueryWrapper<PoiEntity> wrapper = new QueryWrapper<PoiEntity>().eq("map_rank", request.mapRank);
            if (currentId != null) {
                wrapper.ne("id", currentId);
            }
            Long count = poiMapper.selectCount(wrapper);
            if (count != null && count > 0) {
                throw new BizException("Map Rank 已被其他地点使用，请更换或留空");
            }
        }
    }

    private String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return "null".equalsIgnoreCase(text) ? "" : text;
    }

    private String storePoiImage(Long poiId, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BizException("请选择要上传的地点图片");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new BizException("地点图片不能超过 5MB");
        }
        String contentType = image.getContentType() == null ? "" : image.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BizException("地点图片只支持 JPG、PNG、WEBP 或 GIF");
        }
        Path directory = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            String filename = "poi-" + poiId + "-" + UUID.randomUUID() + imageExtension(image);
            Path target = directory.resolve(filename).normalize();
            if (!target.startsWith(directory)) {
                throw new BizException("地点图片文件名不合法");
            }
            try (InputStream input = image.getInputStream()) {
                Files.copy(input, target);
            }
            return "/uploads/poi/" + filename;
        } catch (IOException ex) {
            throw new BizException("地点图片保存失败");
        }
    }

    private String imageExtension(MultipartFile image) {
        String extension = StringUtils.getFilenameExtension(image.getOriginalFilename());
        if (StringUtils.hasText(extension)) {
            String normalized = extension.toLowerCase(Locale.ROOT);
            if (Set.of("jpg", "jpeg", "png", "webp", "gif").contains(normalized)) {
                return "." + normalized;
            }
        }
        return switch (image.getContentType() == null ? "" : image.getContentType().toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }

    private void deleteLocalImageIfManaged(String imageUrl) {
        String managedPrefix = "/uploads/poi/";
        if (!StringUtils.hasText(imageUrl) || !imageUrl.startsWith(managedPrefix)) {
            return;
        }
        Path directory = Paths.get(uploadDir).toAbsolutePath().normalize();
        String filename = imageUrl.substring(managedPrefix.length());
        Path target = directory.resolve(filename).normalize();
        if (!target.startsWith(directory)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
        }
    }

    private void validateCoordinate(BigDecimal longitude, BigDecimal latitude) {
        if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0
                || latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new BizException("POI coordinate is outside valid longitude or latitude range");
        }
    }
}
