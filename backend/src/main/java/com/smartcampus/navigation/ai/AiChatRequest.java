package com.smartcampus.navigation.ai;

import jakarta.validation.constraints.NotBlank;

public class AiChatRequest {
    @NotBlank
    public String message;
}

