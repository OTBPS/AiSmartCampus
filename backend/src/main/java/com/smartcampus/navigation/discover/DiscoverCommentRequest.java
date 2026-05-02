package com.smartcampus.navigation.discover;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DiscoverCommentRequest {
    @NotBlank
    @Size(max = 600)
    public String content;
}
