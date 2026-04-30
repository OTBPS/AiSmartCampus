package com.smartcampus.navigation.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank
    public String username;
    @NotBlank
    @Size(min = 6)
    public String password;
    @NotBlank
    public String displayName;
}

