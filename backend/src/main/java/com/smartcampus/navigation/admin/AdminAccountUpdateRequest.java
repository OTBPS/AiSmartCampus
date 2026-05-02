package com.smartcampus.navigation.admin;

import com.smartcampus.navigation.user.AccountRules;
import jakarta.validation.constraints.Pattern;

public class AdminAccountUpdateRequest {
    @Pattern(regexp = AccountRules.USERNAME_PATTERN, message = AccountRules.USERNAME_MESSAGE)
    public String username;

    public String password;

    public String status;
}
