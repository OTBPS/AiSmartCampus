package com.smartcampus.navigation.discover;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcampus.navigation.common.BizException;
import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiMapper;
import com.smartcampus.navigation.user.UserEntity;
import com.smartcampus.navigation.user.UserMapper;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DiscoverService {
    private static final int SUMMARY_LENGTH = 180;

    private final DiscoverPostMapper postMapper;
    private final DiscoverCommentMapper commentMapper;
    private final DiscoverLikeMapper likeMapper;
    private final DiscoverFavoriteMapper favoriteMapper;
    private final PoiMapper poiMapper;
    private final UserMapper userMapper;

    public DiscoverService(
            DiscoverPostMapper postMapper,
            DiscoverCommentMapper commentMapper,
            DiscoverLikeMapper likeMapper,
            DiscoverFavoriteMapper favoriteMapper,
            PoiMapper poiMapper,
            UserMapper userMapper
    ) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.likeMapper = likeMapper;
        this.favoriteMapper = favoriteMapper;
        this.poiMapper = poiMapper;
        this.userMapper = userMapper;
    }

    public List<DiscoverPostResponse> listPublished(String sort, Long userId) {
        List<DiscoverPostResponse> responses = postMapper.selectList(
                        new QueryWrapper<DiscoverPostEntity>()
                                .eq("status", "PUBLISHED")
                                .orderByDesc("created_at")
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
        response.coverUrl = post.coverUrl;
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

    private String normalizeSort(String sort) {
        return sort == null ? "TIME" : sort.toUpperCase(Locale.ROOT);
    }
}
