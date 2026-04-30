package com.smartcampus.navigation.security;

import com.smartcampus.navigation.common.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUsers {
    private SecurityUsers() {
    }

    public static JwtTokenService.TokenUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtTokenService.TokenUser user)) {
            throw new BizException("Unauthenticated");
        }
        return user;
    }
}

