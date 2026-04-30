package com.smartcampus.navigation.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcampus.navigation.common.BizException;
import com.smartcampus.navigation.security.JwtTokenService;
import com.smartcampus.navigation.user.UserEntity;
import com.smartcampus.navigation.user.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public AuthResponse login(LoginRequest request) {
        UserEntity user = findByUsername(request.username);
        if (user == null || !"ACTIVE".equals(user.status) || !passwordEncoder.matches(request.password, user.passwordHash)) {
            throw new BizException("用户名或密码错误");
        }
        return toResponse(user);
    }

    public AuthResponse register(RegisterRequest request) {
        if (findByUsername(request.username) != null) {
            throw new BizException("用户名已存在");
        }
        UserEntity user = new UserEntity();
        user.username = request.username;
        user.displayName = request.displayName;
        user.passwordHash = passwordEncoder.encode(request.password);
        user.role = "USER";
        user.status = "ACTIVE";
        userMapper.insert(user);
        return toResponse(user);
    }

    private UserEntity findByUsername(String username) {
        return userMapper.selectOne(new QueryWrapper<UserEntity>().eq("username", username));
    }

    private AuthResponse toResponse(UserEntity user) {
        AuthResponse response = new AuthResponse();
        response.token = jwtTokenService.generate(user);
        response.userId = user.id;
        response.username = user.username;
        response.displayName = user.displayName;
        response.role = user.role;
        return response;
    }
}
