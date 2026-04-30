package com.smartcampus.navigation.auth;

import com.smartcampus.navigation.common.ApiResponse;
import com.smartcampus.navigation.security.JwtTokenService;
import com.smartcampus.navigation.security.SecurityUsers;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @GetMapping("/me")
    public ApiResponse<JwtTokenService.TokenUser> me() {
        return ApiResponse.ok(SecurityUsers.current());
    }
}

