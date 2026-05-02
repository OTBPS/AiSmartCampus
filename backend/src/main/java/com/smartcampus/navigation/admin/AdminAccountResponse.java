package com.smartcampus.navigation.admin;

import com.smartcampus.navigation.user.UserEntity;
import java.time.LocalDateTime;

public class AdminAccountResponse {
    public Long id;
    public String username;
    public String displayName;
    public String role;
    public String status;
    public LocalDateTime createdAt;

    public static AdminAccountResponse from(UserEntity user) {
        AdminAccountResponse response = new AdminAccountResponse();
        response.id = user.id;
        response.username = user.username;
        response.displayName = user.displayName;
        response.role = user.role;
        response.status = user.status;
        response.createdAt = user.createdAt;
        return response;
    }
}
