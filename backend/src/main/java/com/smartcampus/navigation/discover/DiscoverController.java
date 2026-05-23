package com.smartcampus.navigation.discover;

import com.smartcampus.navigation.common.ApiResponse;
import com.smartcampus.navigation.security.JwtTokenService;
import com.smartcampus.navigation.security.SecurityUsers;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/discover")
public class DiscoverController {
    private final DiscoverService discoverService;

    public DiscoverController(DiscoverService discoverService) {
        this.discoverService = discoverService;
    }

    @GetMapping("/posts")
    public ApiResponse<List<DiscoverPostResponse>> posts(
            @RequestParam(defaultValue = "TIME") String sort,
            @RequestParam(required = false) String keyword
    ) {
        JwtTokenService.TokenUser user = SecurityUsers.current();
        return ApiResponse.ok(discoverService.listPublished(sort, keyword, user.id()));
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<DiscoverPostResponse> post(@PathVariable Long id) {
        JwtTokenService.TokenUser user = SecurityUsers.current();
        return ApiResponse.ok(discoverService.get(id, user.id(), user.role()));
    }

    @PostMapping("/posts")
    public ApiResponse<DiscoverPostResponse> create(@Valid @RequestBody DiscoverPostRequest request) {
        JwtTokenService.TokenUser user = SecurityUsers.current();
        return ApiResponse.ok(discoverService.create(user.id(), request));
    }

    @PutMapping("/posts/{id}")
    public ApiResponse<DiscoverPostResponse> update(@PathVariable Long id, @Valid @RequestBody DiscoverPostRequest request) {
        JwtTokenService.TokenUser user = SecurityUsers.current();
        return ApiResponse.ok(discoverService.update(id, user.id(), request));
    }

    @PostMapping(value = "/posts/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DiscoverPostResponse> uploadImages(@PathVariable Long id, @RequestParam("images") List<MultipartFile> images) {
        JwtTokenService.TokenUser user = SecurityUsers.current();
        return ApiResponse.ok(discoverService.uploadImages(id, user.id(), images));
    }

    @DeleteMapping("/posts/{postId}/images/{imageId}")
    public ApiResponse<DiscoverPostResponse> deleteImage(@PathVariable Long postId, @PathVariable Long imageId) {
        JwtTokenService.TokenUser user = SecurityUsers.current();
        return ApiResponse.ok(discoverService.deleteImage(postId, imageId, user.id()));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> deleteOwn(@PathVariable Long id) {
        discoverService.deleteOwn(id, SecurityUsers.current().id());
        return ApiResponse.ok(null);
    }

    @PostMapping("/posts/{id}/like")
    public ApiResponse<DiscoverPostResponse> like(@PathVariable Long id) {
        return ApiResponse.ok(discoverService.like(id, SecurityUsers.current().id()));
    }

    @DeleteMapping("/posts/{id}/like")
    public ApiResponse<DiscoverPostResponse> unlike(@PathVariable Long id) {
        return ApiResponse.ok(discoverService.unlike(id, SecurityUsers.current().id()));
    }

    @PostMapping("/posts/{id}/favorite")
    public ApiResponse<DiscoverPostResponse> favorite(@PathVariable Long id) {
        return ApiResponse.ok(discoverService.favorite(id, SecurityUsers.current().id()));
    }

    @DeleteMapping("/posts/{id}/favorite")
    public ApiResponse<DiscoverPostResponse> unfavorite(@PathVariable Long id) {
        return ApiResponse.ok(discoverService.unfavorite(id, SecurityUsers.current().id()));
    }

    @PostMapping("/posts/{id}/comments")
    public ApiResponse<DiscoverCommentResponse> addComment(@PathVariable Long id, @Valid @RequestBody DiscoverCommentRequest request) {
        return ApiResponse.ok(discoverService.addComment(id, SecurityUsers.current().id(), request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(@PathVariable Long commentId) {
        discoverService.deleteComment(commentId, SecurityUsers.current().id());
        return ApiResponse.ok(null);
    }

    @GetMapping("/mine")
    public ApiResponse<List<DiscoverPostResponse>> mine() {
        return ApiResponse.ok(discoverService.mine(SecurityUsers.current().id()));
    }

    @GetMapping("/favorites")
    public ApiResponse<List<DiscoverPostResponse>> favorites() {
        return ApiResponse.ok(discoverService.favorites(SecurityUsers.current().id()));
    }

    @GetMapping("/admin/posts")
    public ApiResponse<List<DiscoverPostResponse>> adminPosts() {
        return ApiResponse.ok(discoverService.adminList(SecurityUsers.current().id()));
    }

    @DeleteMapping("/admin/posts/{id}")
    public ApiResponse<Void> adminDelete(@PathVariable Long id) {
        discoverService.adminDelete(id);
        return ApiResponse.ok(null);
    }
}
