package com.smartcampus.navigation.feedback;

import jakarta.validation.constraints.NotBlank;

public class FeedbackReviewRequest {
    @NotBlank
    public String status;
    public String reviewNote = "";
    public String poiOpenStatus;
    public String poiRemark;
}
