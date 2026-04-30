package com.smartcampus.navigation.discover;

import com.smartcampus.navigation.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discover")
public class DiscoverController {
    private final DiscoverService discoverService;

    public DiscoverController(DiscoverService discoverService) {
        this.discoverService = discoverService;
    }

    @GetMapping("/posts")
    public ApiResponse<List<DiscoverPostEntity>> posts() {
        return ApiResponse.ok(discoverService.listPublished());
    }

    @GetMapping("/admin/posts")
    public ApiResponse<List<DiscoverPostEntity>> adminPosts() {
        return ApiResponse.ok(discoverService.adminList());
    }

    @PostMapping("/admin/posts")
    public ApiResponse<DiscoverPostEntity> create(@Valid @RequestBody DiscoverPostRequest request) {
        return ApiResponse.ok(discoverService.create(request));
    }

    @PutMapping("/admin/posts/{id}")
    public ApiResponse<DiscoverPostEntity> update(@PathVariable Long id, @Valid @RequestBody DiscoverPostRequest request) {
        return ApiResponse.ok(discoverService.update(id, request));
    }
}

