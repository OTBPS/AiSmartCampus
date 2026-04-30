package com.smartcampus.navigation.discover;

import jakarta.validation.constraints.NotBlank;

public class DiscoverPostRequest {
    @NotBlank
    public String title;
    @NotBlank
    public String summary;
    public Long poiId;
    @NotBlank
    public String category;
    public String coverUrl = "";
    public String status = "PUBLISHED";
}

