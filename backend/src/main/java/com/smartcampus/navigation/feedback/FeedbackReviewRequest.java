package com.smartcampus.navigation.feedback;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public class FeedbackReviewRequest {
    @NotBlank
    public String status;
    public String reviewNote = "";
    public String poiOpenStatus;
    public String poiRemark;
    public BigDecimal poiLongitude;
    public BigDecimal poiLatitude;
}
