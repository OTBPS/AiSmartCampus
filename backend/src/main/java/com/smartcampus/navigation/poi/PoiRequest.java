package com.smartcampus.navigation.poi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class PoiRequest {
    @NotBlank
    public String name;
    @NotBlank
    public String category;
    @NotNull
    public BigDecimal longitude;
    @NotNull
    public BigDecimal latitude;
    @NotBlank
    public String locationText;
    public String openStatus = "OPEN";
    public String tags = "";
    public Boolean sheltered = false;
    public String remark = "";
    public Boolean enabled = true;
}

