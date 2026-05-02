package com.smartcampus.navigation.discover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcampus.navigation.common.BizException;
import com.smartcampus.navigation.poi.PoiEntity;
import com.smartcampus.navigation.poi.PoiMapper;
import com.smartcampus.navigation.user.UserEntity;
import com.smartcampus.navigation.user.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DiscoverServiceTest {
    @Test
    void listPublishedCanSortByLikeCount() {
        TestContext ctx = new TestContext();
        DiscoverPostEntity first = post(1L, 7L);
        first.title = "First";
        DiscoverPostEntity second = post(2L, 7L);
        second.title = "Second";
        when(ctx.postMapper.selectList(any())).thenReturn(List.of(first, second));
        ctx.stubCounts();
        when(ctx.likeMapper.selectCount(any())).thenReturn(1L, 4L);

        List<DiscoverPostResponse> posts = ctx.service().listPublished("LIKES", 7L);

        assertEquals(2L, posts.get(0).id);
        assertEquals(1L, posts.get(1).id);
    }

    @Test
    void searchPublishedNotesMatchesPoiAliasesSortsByLikesAndLimits() {
        TestContext ctx = new TestContext();
        List<DiscoverPostEntity> posts = List.of(
                post(1L, 7L),
                post(2L, 7L),
                post(3L, 7L),
                post(4L, 7L),
                post(5L, 7L),
                post(6L, 7L)
        );
        posts.forEach(post -> {
            post.title = "Library note " + post.id;
            post.body = "Focused study near the library";
            post.summary = post.body;
        });
        when(ctx.postMapper.selectList(any())).thenReturn(posts);
        ctx.stubCounts();
        when(ctx.likeMapper.selectCount(any())).thenReturn(1L, 5L, 3L, 7L, 2L, 6L);

        List<DiscoverPostResponse> results = ctx.service().searchPublishedNotes("\u56fe\u4e66\u9986", 7L, 5);

        assertEquals(List.of(4L, 6L, 2L, 3L, 5L), results.stream().map(post -> post.id).toList());
    }

    @Test
    void searchPublishedNotesUsesPopularNotesWhenKeywordIsBlank() {
        TestContext ctx = new TestContext();
        DiscoverPostEntity first = post(1L, 7L);
        first.title = "Library note";
        DiscoverPostEntity second = post(2L, 7L);
        second.title = "Canteen note";
        when(ctx.postMapper.selectList(any())).thenReturn(List.of(first, second));
        ctx.stubCounts();
        when(ctx.likeMapper.selectCount(any())).thenReturn(1L, 4L);

        List<DiscoverPostResponse> results = ctx.service().searchPublishedNotes(" ", 7L, 5);

        assertEquals(List.of(2L, 1L), results.stream().map(post -> post.id).toList());
    }

    @Test
    void searchPublishedNotesReturnsEmptyWhenNothingMatches() {
        TestContext ctx = new TestContext();
        DiscoverPostEntity post = post(1L, 7L);
        post.title = "Gym note";
        post.summary = "Sports training";
        post.body = "Sports training";
        post.poiId = 10L;
        when(ctx.poiMapper.selectById(10L)).thenReturn(poi(10L, "NUIST Gymnasium", "SPORTS", "sports,gym"));
        when(ctx.postMapper.selectList(any())).thenReturn(List.of(post));
        ctx.stubCounts();

        List<DiscoverPostResponse> results = ctx.service().searchPublishedNotes("library", 7L, 5);

        assertEquals(List.of(), results);
    }

    @Test
    void createStoresOwnerAndDerivesCategoryFromPoi() {
        TestContext ctx = new TestContext();
        AtomicReference<DiscoverPostEntity> saved = new AtomicReference<>();
        when(ctx.postMapper.insert(any(DiscoverPostEntity.class))).thenAnswer(invocation -> {
            DiscoverPostEntity entity = invocation.getArgument(0);
            entity.id = 10L;
            saved.set(entity);
            return 1;
        });
        when(ctx.postMapper.selectById(10L)).thenAnswer(invocation -> saved.get());
        ctx.stubCounts();

        DiscoverPostResponse response = ctx.service().create(7L, request());

        assertEquals(10L, response.id);
        assertEquals(7L, response.userId);
        assertEquals("STUDY", response.category);
        verify(ctx.postMapper).insert(argThat((DiscoverPostEntity entity) ->
                entity.userId.equals(7L)
                        && entity.poiId.equals(9L)
                        && "STUDY".equals(entity.category)
                        && entity.summary.startsWith("Campus library")
        ));
    }

    @Test
    void updateRejectsNonOwner() {
        TestContext ctx = new TestContext();
        DiscoverPostEntity existing = post(3L, 8L);
        when(ctx.postMapper.selectById(3L)).thenReturn(existing);

        BizException ex = assertThrows(BizException.class, () -> ctx.service().update(3L, 7L, request()));

        assertEquals("Only the note owner can change it", ex.getMessage());
        verify(ctx.postMapper, never()).updateById(any(DiscoverPostEntity.class));
    }

    @Test
    void adminDeleteRemovesPostGraph() {
        TestContext ctx = new TestContext();
        DiscoverPostEntity existing = post(3L, 8L);
        when(ctx.postMapper.selectById(3L)).thenReturn(existing);

        ctx.service().adminDelete(3L);

        verify(ctx.commentMapper).delete(any());
        verify(ctx.likeMapper).delete(any());
        verify(ctx.favoriteMapper).delete(any());
        verify(ctx.postMapper).deleteById(3L);
    }

    @Test
    void likeIsIdempotentWhenUserAlreadyLiked() {
        TestContext ctx = new TestContext();
        DiscoverPostEntity existing = post(4L, 8L);
        DiscoverLikeEntity like = new DiscoverLikeEntity();
        like.postId = 4L;
        like.userId = 7L;
        when(ctx.postMapper.selectById(4L)).thenReturn(existing);
        ctx.stubCounts();
        when(ctx.likeMapper.selectOne(any())).thenReturn(like);

        DiscoverPostResponse response = ctx.service().like(4L, 7L);

        assertEquals(4L, response.id);
        verify(ctx.likeMapper, never()).insert(any(DiscoverLikeEntity.class));
    }

    @Test
    void deleteCommentRejectsNonOwner() {
        TestContext ctx = new TestContext();
        DiscoverCommentEntity comment = new DiscoverCommentEntity();
        comment.id = 5L;
        comment.userId = 8L;
        when(ctx.commentMapper.selectById(5L)).thenReturn(comment);

        BizException ex = assertThrows(BizException.class, () -> ctx.service().deleteComment(5L, 7L));

        assertEquals("Only the comment owner can delete it", ex.getMessage());
        verify(ctx.commentMapper, never()).deleteById(5L);
    }

    private DiscoverPostRequest request() {
        DiscoverPostRequest request = new DiscoverPostRequest();
        request.title = "Library note";
        request.body = "Campus library is calm, bright, and useful for focused study sessions.";
        request.poiId = 9L;
        request.rating = 5;
        return request;
    }

    private DiscoverPostEntity post(Long id, Long userId) {
        DiscoverPostEntity post = new DiscoverPostEntity();
        post.id = id;
        post.userId = userId;
        post.title = "Note " + id;
        post.summary = "Summary " + id;
        post.body = "Body " + id;
        post.poiId = 9L;
        post.category = "STUDY";
        post.coverUrl = "";
        post.status = "PUBLISHED";
        post.rating = 4;
        post.createdAt = LocalDateTime.now().minusDays(id);
        return post;
    }

    private static class TestContext {
        final DiscoverPostMapper postMapper = mock(DiscoverPostMapper.class);
        final DiscoverCommentMapper commentMapper = mock(DiscoverCommentMapper.class);
        final DiscoverLikeMapper likeMapper = mock(DiscoverLikeMapper.class);
        final DiscoverFavoriteMapper favoriteMapper = mock(DiscoverFavoriteMapper.class);
        final PoiMapper poiMapper = mock(PoiMapper.class);
        final UserMapper userMapper = mock(UserMapper.class);

        TestContext() {
            PoiEntity poi = poi(9L, "Library", "STUDY", "quiet,study,library");
            when(poiMapper.selectById(9L)).thenReturn(poi);

            UserEntity user = new UserEntity();
            user.id = 7L;
            user.username = "student";
            user.displayName = "Student User";
            when(userMapper.selectById(any())).thenReturn(user);
        }

        DiscoverService service() {
            return new DiscoverService(postMapper, commentMapper, likeMapper, favoriteMapper, poiMapper, userMapper);
        }

        void stubCounts() {
            when(likeMapper.selectCount(any())).thenReturn(0L);
            when(favoriteMapper.selectCount(any())).thenReturn(0L);
            when(commentMapper.selectCount(any())).thenReturn(0L);
            when(favoriteMapper.selectOne(any())).thenReturn(null);
            when(likeMapper.selectOne(any())).thenReturn(null);
            when(commentMapper.selectList(any())).thenReturn(List.of());
        }
    }

    private static PoiEntity poi(Long id, String name, String category, String tags) {
        PoiEntity poi = new PoiEntity();
        poi.id = id;
        poi.name = name;
        poi.category = category;
        poi.locationText = "Central campus";
        poi.openStatus = "OPEN";
        poi.tags = tags;
        return poi;
    }
}
