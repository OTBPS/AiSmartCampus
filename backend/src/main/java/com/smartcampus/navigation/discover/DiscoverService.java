package com.smartcampus.navigation.discover;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcampus.navigation.common.BizException;
import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiMapper;
import com.smartcampus.navigation.user.UserEntity;
import com.smartcampus.navigation.user.UserMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DiscoverService {
    private static final int SUMMARY_LENGTH = 180;
    private static final int MAX_IMAGES_PER_POST = 3;
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final DiscoverPostMapper postMapper;
    private final DiscoverPostImageMapper imageMapper;
    private final DiscoverCommentMapper commentMapper;
    private final DiscoverLikeMapper likeMapper;
    private final DiscoverFavoriteMapper favoriteMapper;
    private final PoiMapper poiMapper;
    private final UserMapper userMapper;
    private final String uploadDir;

    public DiscoverService(
            DiscoverPostMapper postMapper,
            DiscoverCommentMapper commentMapper,
            DiscoverLikeMapper likeMapper,
            DiscoverFavoriteMapper favoriteMapper,
            PoiMapper poiMapper,
            UserMapper userMapper
    ) {
        this(postMapper, null, commentMapper, likeMapper, favoriteMapper, poiMapper, userMapper, "uploads/discover");
    }

    @Autowired
    public DiscoverService(
            DiscoverPostMapper postMapper,
            DiscoverPostImageMapper imageMapper,
            DiscoverCommentMapper commentMapper,
            DiscoverLikeMapper likeMapper,
            DiscoverFavoriteMapper favoriteMapper,
            PoiMapper poiMapper,
            UserMapper userMapper,
            @Value("${app.upload.discover-dir:uploads/discover}") String uploadDir
    ) {
        this.postMapper = postMapper;
        this.imageMapper = imageMapper;
        this.commentMapper = commentMapper;
        this.likeMapper = likeMapper;
        this.favoriteMapper = favoriteMapper;
        this.poiMapper = poiMapper;
        this.userMapper = userMapper;
        this.uploadDir = uploadDir;
    }

    public List<DiscoverPostResponse> listPublished(String sort, Long userId) {
        return listPublished(sort, null, userId);
    }

    public List<DiscoverPostResponse> listPublished(String sort, String keyword, Long userId) {
        QueryWrapper<DiscoverPostEntity> query = new QueryWrapper<DiscoverPostEntity>()
                .eq("status", "PUBLISHED");
        if (StringUtils.hasText(keyword)) {
            query.like("title", keyword.trim());
        }
        List<DiscoverPostResponse> responses = postMapper.selectList(
                        query.orderByDesc("created_at")
                ).stream()
                .map(post -> toResponse(post, userId, false))
                .toList();
        if ("LIKES".equals(normalizeSort(sort))) {
            return responses.stream()
                    .sorted(Comparator.comparingLong((DiscoverPostResponse post) -> post.likeCount).reversed()
                            .thenComparing((DiscoverPostResponse post) -> post.createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        return responses;
    }

    public List<DiscoverPostResponse> adminList(Long userId) {
        return postMapper.selectList(new QueryWrapper<DiscoverPostEntity>().orderByDesc("created_at")).stream()
                .map(post -> toResponse(post, userId, false))
                .toList();
    }

    public List<DiscoverPostResponse> mine(Long userId) {
        return postMapper.selectList(
                        new QueryWrapper<DiscoverPostEntity>()
                                .eq("user_id", userId)
                                .orderByDesc("created_at")
                ).stream()
                .map(post -> toResponse(post, userId, false))
                .toList();
    }

    public List<DiscoverPostResponse> favorites(Long userId) {
        return favoriteMapper.selectList(
                        new QueryWrapper<DiscoverFavoriteEntity>()
                                .eq("user_id", userId)
                                .orderByDesc("created_at")
                ).stream()
                .map(favorite -> postMapper.selectById(favorite.postId))
                .filter(Objects::nonNull)
                .filter(post -> "PUBLISHED".equals(post.status))
                .map(post -> toResponse(post, userId, false))
                .toList();
    }

    public List<DiscoverPostResponse> searchPublishedNotes(String keyword, Long userId, int limit) {
        int max = Math.max(0, limit);
        if (max == 0) {
            return List.of();
        }
        List<String> tokens = searchTokens(keyword);
        return postMapper.selectList(
                        new QueryWrapper<DiscoverPostEntity>()
                                .eq("status", "PUBLISHED")
                                .orderByDesc("created_at")
                ).stream()
                .map(post -> toResponse(post, userId, false))
                .filter(post -> tokens.isEmpty() || matchesAnyToken(post, tokens))
                .sorted(Comparator.comparingLong((DiscoverPostResponse post) -> post.likeCount).reversed()
                        .thenComparing((DiscoverPostResponse post) -> post.createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(max)
                .toList();
    }

    public DiscoverPostResponse get(Long id, Long userId, String role) {
        DiscoverPostEntity post = getPost(id);
        boolean owner = Objects.equals(post.userId, userId);
        if (!"PUBLISHED".equals(post.status) && !owner && !"ADMIN".equals(role)) {
            throw new BizException("Discover note is not visible");
        }
        return toResponse(post, userId, true);
    }

    @Transactional
    public DiscoverPostResponse create(Long userId, DiscoverPostRequest request) {
        DiscoverPostEntity entity = new DiscoverPostEntity();
        entity.userId = userId;
        entity.status = "PUBLISHED";
        apply(entity, request);
        postMapper.insert(entity);
        DiscoverPostEntity saved = entity.id == null ? entity : postMapper.selectById(entity.id);
        return toResponse(saved == null ? entity : saved, userId, true);
    }

    @Transactional
    public DiscoverPostResponse update(Long id, Long userId, DiscoverPostRequest request) {
        DiscoverPostEntity entity = getPost(id);
        assertOwner(entity, userId);
        apply(entity, request);
        entity.updatedAt = LocalDateTime.now();
        postMapper.updateById(entity);
        return toResponse(postMapper.selectById(id), userId, true);
    }

    @Transactional
    public DiscoverPostResponse uploadImages(Long postId, Long userId, List<MultipartFile> images) {
        DiscoverPostEntity post = getPost(postId);
        assertOwner(post, userId);
        if (imageMapper == null) {
            throw new BizException("Discover image storage is not available");
        }
        List<MultipartFile> uploadImages = images == null ? List.of() : images.stream()
                .filter(Objects::nonNull)
                .toList();
        if (uploadImages.isEmpty()) {
            throw new BizException("Please select images to upload");
        }
        List<DiscoverPostImageEntity> existing = postImages(postId);
        if (existing.size() + uploadImages.size() > MAX_IMAGES_PER_POST) {
            throw new BizException("Each note can have at most 3 images");
        }
        int nextSortOrder = existing.stream()
                .map(image -> image.sortOrder == null ? 0 : image.sortOrder)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        List<String> storedUrls = new ArrayList<>();
        try {
            for (MultipartFile image : uploadImages) {
                String imageUrl = storeDiscoverImage(postId, image);
                storedUrls.add(imageUrl);
                DiscoverPostImageEntity entity = new DiscoverPostImageEntity();
                entity.postId = postId;
                entity.imageUrl = imageUrl;
                entity.sortOrder = nextSortOrder++;
                imageMapper.insert(entity);
            }
        } catch (RuntimeException ex) {
            storedUrls.forEach(this::deleteLocalImageIfManaged);
            throw ex;
        }
        return toResponse(postMapper.selectById(postId), userId, true);
    }

    @Transactional
    public DiscoverPostResponse deleteImage(Long postId, Long imageId, Long userId) {
        DiscoverPostEntity post = getPost(postId);
        assertOwner(post, userId);
        if (imageMapper == null) {
            throw new BizException("Discover image storage is not available");
        }
        DiscoverPostImageEntity image = imageMapper.selectById(imageId);
        if (image == null || !Objects.equals(image.postId, postId)) {
            throw new BizException("Discover note image not found");
        }
        imageMapper.deleteById(imageId);
        deleteLocalImageIfManaged(image.imageUrl);
        return toResponse(postMapper.selectById(postId), userId, true);
    }

    @Transactional
    public void deleteOwn(Long id, Long userId) {
        DiscoverPostEntity entity = getPost(id);
        assertOwner(entity, userId);
        deletePostGraph(id);
    }

    @Transactional
    public void adminDelete(Long id) {
        getPost(id);
        deletePostGraph(id);
    }

    @Transactional
    public DiscoverPostResponse like(Long postId, Long userId) {
        DiscoverPostEntity post = requirePublished(postId);
        if (likeByPostAndUser(postId, userId) == null) {
            DiscoverLikeEntity entity = new DiscoverLikeEntity();
            entity.postId = postId;
            entity.userId = userId;
            likeMapper.insert(entity);
        }
        return toResponse(post, userId, false);
    }

    @Transactional
    public DiscoverPostResponse unlike(Long postId, Long userId) {
        DiscoverPostEntity post = requirePublished(postId);
        likeMapper.delete(new QueryWrapper<DiscoverLikeEntity>().eq("post_id", postId).eq("user_id", userId));
        return toResponse(post, userId, false);
    }

    @Transactional
    public DiscoverPostResponse favorite(Long postId, Long userId) {
        DiscoverPostEntity post = requirePublished(postId);
        if (favoriteByPostAndUser(postId, userId) == null) {
            DiscoverFavoriteEntity entity = new DiscoverFavoriteEntity();
            entity.postId = postId;
            entity.userId = userId;
            favoriteMapper.insert(entity);
        }
        return toResponse(post, userId, false);
    }

    @Transactional
    public DiscoverPostResponse unfavorite(Long postId, Long userId) {
        DiscoverPostEntity post = requirePublished(postId);
        favoriteMapper.delete(new QueryWrapper<DiscoverFavoriteEntity>().eq("post_id", postId).eq("user_id", userId));
        return toResponse(post, userId, false);
    }

    @Transactional
    public DiscoverCommentResponse addComment(Long postId, Long userId, DiscoverCommentRequest request) {
        requirePublished(postId);
        DiscoverCommentEntity entity = new DiscoverCommentEntity();
        entity.postId = postId;
        entity.userId = userId;
        entity.content = request.content.trim();
        commentMapper.insert(entity);
        return toCommentResponse(entity, userId);
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        DiscoverCommentEntity comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException("Comment not found");
        }
        if (!Objects.equals(comment.userId, userId)) {
            throw new BizException("Only the comment owner can delete it");
        }
        commentMapper.deleteById(commentId);
    }

    private void apply(DiscoverPostEntity entity, DiscoverPostRequest request) {
        PoiEntity poi = poiMapper.selectById(request.poiId);
        if (poi == null) {
            throw new BizException("Related POI not found");
        }
        int rating = request.rating == null ? 4 : request.rating;
        if (rating < 1 || rating > 5) {
            throw new BizException("Rating must be between 1 and 5");
        }
        String body = request.body == null ? "" : request.body.trim();
        if (!StringUtils.hasText(body)) {
            throw new BizException("Note body is required");
        }
        entity.title = request.title == null ? "" : request.title.trim();
        entity.body = body;
        entity.summary = summarize(body);
        entity.poiId = request.poiId;
        entity.category = poi.category;
        entity.coverUrl = request.coverUrl == null ? "" : request.coverUrl.trim();
        entity.rating = rating;
    }

    private DiscoverPostEntity requirePublished(Long postId) {
        DiscoverPostEntity post = getPost(postId);
        if (!"PUBLISHED".equals(post.status)) {
            throw new BizException("Discover note is not visible");
        }
        return post;
    }

    private DiscoverPostEntity getPost(Long id) {
        DiscoverPostEntity post = postMapper.selectById(id);
        if (post == null) {
            throw new BizException("Discover note not found");
        }
        return post;
    }

    private void assertOwner(DiscoverPostEntity post, Long userId) {
        if (!Objects.equals(post.userId, userId)) {
            throw new BizException("Only the note owner can change it");
        }
    }

    private void deletePostGraph(Long postId) {
        List<DiscoverPostImageEntity> images = postImages(postId);
        if (imageMapper != null) {
            imageMapper.delete(new QueryWrapper<DiscoverPostImageEntity>().eq("post_id", postId));
        }
        images.forEach(image -> deleteLocalImageIfManaged(image.imageUrl));
        commentMapper.delete(new QueryWrapper<DiscoverCommentEntity>().eq("post_id", postId));
        likeMapper.delete(new QueryWrapper<DiscoverLikeEntity>().eq("post_id", postId));
        favoriteMapper.delete(new QueryWrapper<DiscoverFavoriteEntity>().eq("post_id", postId));
        postMapper.deleteById(postId);
    }

    private DiscoverPostResponse toResponse(DiscoverPostEntity post, Long userId, boolean includeComments) {
        DiscoverPostResponse response = new DiscoverPostResponse();
        response.id = post.id;
        response.userId = post.userId;
        response.authorName = authorName(post.userId);
        response.title = post.title;
        response.summary = post.summary;
        response.body = post.body;
        response.poiId = post.poiId;
        response.category = post.category;
        response.images = postImages(post.id).stream()
                .map(this::toImageResponse)
                .toList();
        response.coverUrl = response.images.isEmpty() ? post.coverUrl : response.images.get(0).imageUrl;
        response.status = post.status;
        response.rating = post.rating;
        response.createdAt = post.createdAt;
        response.updatedAt = post.updatedAt;
        response.likeCount = countLikes(post.id);
        response.favoriteCount = countFavorites(post.id);
        response.commentCount = countComments(post.id);
        response.liked = userId != null && likeByPostAndUser(post.id, userId) != null;
        response.favorited = userId != null && favoriteByPostAndUser(post.id, userId) != null;
        response.owner = userId != null && Objects.equals(post.userId, userId);
        applyPoi(response, post.poiId);
        if (includeComments) {
            response.comments = commentMapper.selectList(
                            new QueryWrapper<DiscoverCommentEntity>()
                                    .eq("post_id", post.id)
                                    .orderByAsc("created_at")
                    ).stream()
                    .map(comment -> toCommentResponse(comment, userId))
                    .toList();
        }
        return response;
    }

    private DiscoverPostImageResponse toImageResponse(DiscoverPostImageEntity image) {
        DiscoverPostImageResponse response = new DiscoverPostImageResponse();
        response.id = image.id;
        response.imageUrl = image.imageUrl;
        response.sortOrder = image.sortOrder;
        response.createdAt = image.createdAt;
        return response;
    }

    private List<DiscoverPostImageEntity> postImages(Long postId) {
        if (postId == null || imageMapper == null) {
            return List.of();
        }
        return imageMapper.selectList(
                new QueryWrapper<DiscoverPostImageEntity>()
                        .eq("post_id", postId)
                        .orderByAsc("sort_order")
                        .orderByAsc("id")
        );
    }

    private DiscoverCommentResponse toCommentResponse(DiscoverCommentEntity comment, Long userId) {
        DiscoverCommentResponse response = new DiscoverCommentResponse();
        response.id = comment.id;
        response.postId = comment.postId;
        response.userId = comment.userId;
        response.authorName = authorName(comment.userId);
        response.content = comment.content;
        response.owner = userId != null && Objects.equals(comment.userId, userId);
        response.createdAt = comment.createdAt;
        return response;
    }

    private void applyPoi(DiscoverPostResponse response, Long poiId) {
        PoiEntity poi = poiId == null ? null : poiMapper.selectById(poiId);
        if (poi == null) {
            return;
        }
        response.poiName = poi.name;
        response.poiCategory = poi.category;
        response.poiLocationText = poi.locationText;
        response.poiOpenStatus = poi.openStatus;
        response.poiTags = poi.tags;
    }

    private String authorName(Long userId) {
        UserEntity user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            return "Unknown user";
        }
        return StringUtils.hasText(user.displayName) ? user.displayName : user.username;
    }

    private long countLikes(Long postId) {
        return likeMapper.selectCount(new QueryWrapper<DiscoverLikeEntity>().eq("post_id", postId));
    }

    private long countFavorites(Long postId) {
        return favoriteMapper.selectCount(new QueryWrapper<DiscoverFavoriteEntity>().eq("post_id", postId));
    }

    private long countComments(Long postId) {
        return commentMapper.selectCount(new QueryWrapper<DiscoverCommentEntity>().eq("post_id", postId));
    }

    private DiscoverLikeEntity likeByPostAndUser(Long postId, Long userId) {
        return likeMapper.selectOne(new QueryWrapper<DiscoverLikeEntity>().eq("post_id", postId).eq("user_id", userId).last("LIMIT 1"));
    }

    private DiscoverFavoriteEntity favoriteByPostAndUser(Long postId, Long userId) {
        return favoriteMapper.selectOne(new QueryWrapper<DiscoverFavoriteEntity>().eq("post_id", postId).eq("user_id", userId).last("LIMIT 1"));
    }

    private String summarize(String body) {
        String normalized = body.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= SUMMARY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, SUMMARY_LENGTH - 3) + "...";
    }

    private String storeDiscoverImage(Long postId, MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BizException("Please select images to upload");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new BizException("Note image cannot exceed 5MB");
        }
        String contentType = image.getContentType() == null ? "" : image.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BizException("Note images only support JPG, PNG, WEBP, or GIF");
        }
        Path directory = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            String filename = "note-" + postId + "-" + UUID.randomUUID() + imageExtension(image);
            Path target = directory.resolve(filename).normalize();
            if (!target.startsWith(directory)) {
                throw new BizException("Invalid note image filename");
            }
            try (InputStream input = image.getInputStream()) {
                Files.copy(input, target);
            }
            return "/uploads/discover/" + filename;
        } catch (IOException ex) {
            throw new BizException("Note image save failed");
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
        if (!StringUtils.hasText(imageUrl) || !imageUrl.startsWith("/uploads/")) {
            return;
        }
        Path directory = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path root = directory.getParent() == null ? directory : directory.getParent();
        String relativePath = imageUrl.substring("/uploads/".length()).replace("/", root.getFileSystem().getSeparator());
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
        }
    }

    private String normalizeSort(String sort) {
        return sort == null ? "TIME" : sort.toUpperCase(Locale.ROOT);
    }

    private List<String> searchTokens(String keyword) {
        String normalized = normalizeSearchText(keyword);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        tokens.add(normalized);
        addAliasToken(tokens, normalized, "\u56fe\u4e66\u9986", "library");
        addAliasToken(tokens, normalized, "\u81ea\u4e60", "study");
        addAliasToken(tokens, normalized, "\u5b66\u4e60", "study");
        addAliasToken(tokens, normalized, "\u98df\u5802", "canteen");
        addAliasToken(tokens, normalized, "\u9910\u5385", "dining");
        addAliasToken(tokens, normalized, "\u6253\u5370", "print");
        addAliasToken(tokens, normalized, "\u590d\u5370", "copy");
        addAliasToken(tokens, normalized, "\u660e\u5fb7", "mingde");
        addAliasToken(tokens, normalized, "\u6587\u5fb7", "wende");
        addAliasToken(tokens, normalized, "\u5c1a\u8d24", "shangxian");
        addAliasToken(tokens, normalized, "\u897f\u82d1", "xiyuan");
        addAliasToken(tokens, normalized, "\u4e2d\u82d1", "zhongyuan");
        addAliasToken(tokens, normalized, "\u4e1c\u82d1", "dongyuan");
        addAliasToken(tokens, normalized, "\u4f53\u80b2\u9986", "gym");
        addAliasToken(tokens, normalized, "\u64cd\u573a", "sports");
        addAliasToken(tokens, normalized, "\u89c2\u6d4b\u573a", "observation");
        addAliasToken(tokens, normalized, "\u6821\u533b\u9662", "clinic");
        return new ArrayList<>(tokens);
    }

    private void addAliasToken(Set<String> tokens, String normalized, String source, String alias) {
        if (normalized.contains(normalizeSearchText(source))) {
            tokens.add(normalizeSearchText(alias));
        }
    }

    private boolean matchesAnyToken(DiscoverPostResponse post, List<String> tokens) {
        String text = normalizeSearchText(String.join(" ",
                nullToEmpty(post.title),
                nullToEmpty(post.summary),
                nullToEmpty(post.body),
                nullToEmpty(post.category),
                nullToEmpty(post.poiName),
                nullToEmpty(post.poiCategory),
                nullToEmpty(post.poiLocationText),
                nullToEmpty(post.poiTags)
        ));
        return tokens.stream().anyMatch(text::contains);
    }

    private String normalizeSearchText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
