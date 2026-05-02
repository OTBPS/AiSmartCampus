package com.smartcampus.navigation.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcampus.navigation.common.BizException;
import com.smartcampus.navigation.security.JwtTokenService;
import com.smartcampus.navigation.user.UserEntity;
import com.smartcampus.navigation.user.UserMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
    @Test
    void registerAlwaysCreatesNormalActiveUserWithUsernameDisplayName() {
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        AtomicReference<UserEntity> saved = new AtomicReference<>();
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("Abc123")).thenReturn("encoded");
        when(userMapper.insert(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.id = 9L;
            saved.set(user);
            return 1;
        });
        when(jwtTokenService.generate(any(UserEntity.class))).thenReturn("token");
        RegisterRequest request = new RegisterRequest();
        request.username = "User_Name";
        request.password = "Abc123";

        AuthResponse response = new AuthService(userMapper, passwordEncoder, jwtTokenService).register(request);

        assertEquals("token", response.token);
        assertEquals("USER", saved.get().role);
        assertEquals("ACTIVE", saved.get().status);
        assertEquals("User_Name", saved.get().displayName);
        assertEquals("encoded", saved.get().passwordHash);
    }

    @Test
    void registerRejectsInvalidUsernameBeforeInsert() {
        UserMapper userMapper = mock(UserMapper.class);
        RegisterRequest request = new RegisterRequest();
        request.username = "user1";
        request.password = "Abc123";

        BizException ex = assertThrows(
                BizException.class,
                () -> new AuthService(userMapper, mock(PasswordEncoder.class), mock(JwtTokenService.class)).register(request)
        );

        assertEquals("Username must be 1-15 English letters or underscore", ex.getMessage());
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

    @Test
    void loginRejectsInactiveUser() {
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserEntity user = new UserEntity();
        user.username = "student";
        user.status = "INACTIVE";
        user.passwordHash = "encoded";
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("123456", "encoded")).thenReturn(true);
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "123456";

        BizException ex = assertThrows(
                BizException.class,
                () -> new AuthService(userMapper, passwordEncoder, mock(JwtTokenService.class)).login(request)
        );

        assertEquals("用户名或密码错误", ex.getMessage());
    }
}
