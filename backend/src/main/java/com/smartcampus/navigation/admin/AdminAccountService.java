package com.smartcampus.navigation.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartcampus.navigation.common.BizException;
import com.smartcampus.navigation.user.AccountRules;
import com.smartcampus.navigation.user.UserEntity;
import com.smartcampus.navigation.user.UserMapper;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAccountService {
    private static final String ROLE_USER = "USER";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public List<AdminAccountResponse> list(String keyword, String status) {
        QueryWrapper<UserEntity> query = new QueryWrapper<UserEntity>()
                .eq("role", ROLE_USER)
                .orderByDesc("created_at")
                .orderByAsc("username");
        if (status != null && !status.isBlank()) {
            if (!AccountRules.isValidStatus(status)) {
                throw new BizException("Invalid account status");
            }
            query.eq("status", status);
        }
        String term = keyword == null ? "" : keyword.trim();
        if (!term.isEmpty()) {
            query.and(wrapper -> wrapper.like("username", term).or().like("display_name", term));
        }
        return userMapper.selectList(query).stream()
                .map(AdminAccountResponse::from)
                .toList();
    }

    public AdminAccountResponse create(AdminAccountCreateRequest request) {
        AccountRules.requireValidUsername(request.username);
        AccountRules.requireValidPassword(request.password);
        ensureUsernameAvailable(request.username, null);

        UserEntity user = new UserEntity();
        user.username = request.username;
        user.displayName = request.username;
        user.passwordHash = passwordEncoder.encode(request.password);
        user.role = ROLE_USER;
        user.status = normalizeStatus(request.status, STATUS_ACTIVE);
        userMapper.insert(user);
        return AdminAccountResponse.from(user);
    }

    public AdminAccountResponse update(Long id, AdminAccountUpdateRequest request) {
        UserEntity user = managedUser(id);
        if (request.username != null && !request.username.isBlank() && !request.username.equals(user.username)) {
            AccountRules.requireValidUsername(request.username);
            ensureUsernameAvailable(request.username, id);
            user.username = request.username;
            user.displayName = request.username;
        }
        if (request.password != null && !request.password.isBlank()) {
            AccountRules.requireValidPassword(request.password);
            user.passwordHash = passwordEncoder.encode(request.password);
        }
        if (request.status != null && !request.status.isBlank()) {
            user.status = normalizeStatus(request.status, user.status);
        }
        userMapper.updateById(user);
        return AdminAccountResponse.from(user);
    }

    public AdminAccountResponse deactivate(Long id) {
        UserEntity user = managedUser(id);
        user.status = STATUS_INACTIVE;
        userMapper.updateById(user);
        return AdminAccountResponse.from(user);
    }

    private UserEntity managedUser(Long id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null || !ROLE_USER.equals(user.role)) {
            throw new BizException("Only normal user accounts can be managed");
        }
        return user;
    }

    private void ensureUsernameAvailable(String username, Long currentId) {
        QueryWrapper<UserEntity> query = new QueryWrapper<UserEntity>()
                .apply("BINARY username = {0}", username);
        if (currentId != null) {
            query.ne("id", currentId);
        }
        if (userMapper.selectCount(query) > 0) {
            throw new BizException("Username already exists");
        }
    }

    private String normalizeStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        if (!AccountRules.isValidStatus(status)) {
            throw new BizException("Invalid account status");
        }
        return status;
    }
}
