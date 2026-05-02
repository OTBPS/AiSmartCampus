package com.smartcampus.navigation.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartcampus.navigation.common.BizException;
import com.smartcampus.navigation.user.UserEntity;
import com.smartcampus.navigation.user.UserMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminAccountServiceTest {
    @Test
    void createStoresOnlyNormalUserAccounts() {
        TestContext ctx = new TestContext();
        AtomicReference<UserEntity> saved = new AtomicReference<>();
        when(ctx.userMapper.selectCount(any())).thenReturn(0L);
        when(ctx.passwordEncoder.encode("Abc123")).thenReturn("encoded");
        when(ctx.userMapper.insert(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.id = 12L;
            saved.set(user);
            return 1;
        });
        AdminAccountCreateRequest request = new AdminAccountCreateRequest();
        request.username = "New_User";
        request.password = "Abc123";

        AdminAccountResponse response = ctx.service.create(request);

        assertEquals(12L, response.id);
        assertEquals("USER", saved.get().role);
        assertEquals("ACTIVE", saved.get().status);
        assertEquals("New_User", saved.get().displayName);
    }

    @Test
    void updateRejectsAdminAccounts() {
        TestContext ctx = new TestContext();
        UserEntity admin = user(1L, "admin", "ADMIN", "ACTIVE");
        when(ctx.userMapper.selectById(1L)).thenReturn(admin);
        AdminAccountUpdateRequest request = new AdminAccountUpdateRequest();
        request.username = "Admin_User";

        BizException ex = assertThrows(BizException.class, () -> ctx.service.update(1L, request));

        assertEquals("Only normal user accounts can be managed", ex.getMessage());
        verify(ctx.userMapper, never()).updateById(any(UserEntity.class));
    }

    @Test
    void deactivateSoftDeletesNormalUser() {
        TestContext ctx = new TestContext();
        UserEntity user = user(2L, "student", "USER", "ACTIVE");
        when(ctx.userMapper.selectById(2L)).thenReturn(user);

        AdminAccountResponse response = ctx.service.deactivate(2L);

        assertEquals("INACTIVE", response.status);
        verify(ctx.userMapper).updateById(user);
    }

    @Test
    void updateBlankPasswordKeepsExistingHash() {
        TestContext ctx = new TestContext();
        UserEntity user = user(3L, "student", "USER", "ACTIVE");
        user.passwordHash = "old";
        when(ctx.userMapper.selectById(3L)).thenReturn(user);
        when(ctx.userMapper.selectCount(any())).thenReturn(0L);
        AdminAccountUpdateRequest request = new AdminAccountUpdateRequest();
        request.username = "Student_User";
        request.password = "";
        request.status = "ACTIVE";

        ctx.service.update(3L, request);

        assertEquals("old", user.passwordHash);
        assertEquals("Student_User", user.username);
        assertEquals("Student_User", user.displayName);
        verify(ctx.passwordEncoder, never()).encode(any());
    }

    private UserEntity user(Long id, String username, String role, String status) {
        UserEntity user = new UserEntity();
        user.id = id;
        user.username = username;
        user.displayName = username;
        user.role = role;
        user.status = status;
        return user;
    }

    private static class TestContext {
        final UserMapper userMapper = mock(UserMapper.class);
        final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        final AdminAccountService service = new AdminAccountService(userMapper, passwordEncoder);
    }
}
