package com.smartcampus.navigation.feedback;

import com.smartcampus.navigation.common.ApiResponse;
import com.smartcampus.navigation.security.SecurityUsers;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ApiResponse<FeedbackEntity> submit(@Valid @RequestBody FeedbackRequest request) {
        return ApiResponse.ok(feedbackService.submit(SecurityUsers.current().id(), request));
    }

    @GetMapping("/mine")
    public ApiResponse<List<FeedbackEntity>> mine() {
        return ApiResponse.ok(feedbackService.mine(SecurityUsers.current().id()));
    }

    @GetMapping("/admin")
    public ApiResponse<List<FeedbackEntity>> adminList(@RequestParam(required = false) String status) {
        return ApiResponse.ok(feedbackService.adminList(status));
    }

    @PutMapping("/admin/{id}/review")
    public ApiResponse<FeedbackEntity> review(@PathVariable Long id, @Valid @RequestBody FeedbackReviewRequest request) {
        return ApiResponse.ok(feedbackService.review(id, request));
    }
}

