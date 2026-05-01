package com.smartcampus.navigation.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class FeedbackRequest {
    @NotNull
    public Long poiId;
    @NotBlank
    public String type;
    @NotBlank
    public String content;
}
