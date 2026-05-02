package com.smartcampus.navigation.auth;

import com.smartcampus.navigation.user.AccountRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class LoginRequest {
    @NotBlank
    @Pattern(regexp = AccountRules.USERNAME_PATTERN, message = AccountRules.USERNAME_MESSAGE)
    public String username;
    @NotBlank
    @Pattern(regexp = AccountRules.PASSWORD_PATTERN, message = AccountRules.PASSWORD_MESSAGE)
    public String password;
}
