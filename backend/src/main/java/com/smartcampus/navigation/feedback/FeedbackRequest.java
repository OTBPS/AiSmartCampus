package com.smartcampus.navigation.feedback;

import jakarta.validation.constraints.NotBlank;

public class FeedbackRequest {
    public Long poiId;
    @NotBlank
    public String type;
    @NotBlank
    public String content;
}

