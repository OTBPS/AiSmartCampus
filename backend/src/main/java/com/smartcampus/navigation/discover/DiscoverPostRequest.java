package com.smartcampus.navigation.discover;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class DiscoverPostRequest {
    @NotBlank
    @Size(max = 120)
    public String title;
    @NotBlank
    @Size(max = 3000)
    public String body;
    @NotNull
    public Long poiId;
    @Min(1)
    @Max(5)
    public Integer rating = 4;
    public String coverUrl = "";
}
