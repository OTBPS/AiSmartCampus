package com.smartcampus.navigation.user;

import com.smartcampus.navigation.common.BizException;

public final class AccountRules {
    public static final String USERNAME_PATTERN = "^[A-Za-z_]{1,15}$";
    public static final String PASSWORD_PATTERN = "^[A-Za-z0-9]{6,20}$";
    public static final String USERNAME_MESSAGE = "must be 1-15 English letters or underscore";
    public static final String PASSWORD_MESSAGE = "must be 6-20 English letters or numbers";

    private AccountRules() {
    }

    public static void requireValidUsername(String username) {
        if (username == null || !username.matches(USERNAME_PATTERN)) {
            throw new BizException("Username " + USERNAME_MESSAGE);
        }
    }

    public static void requireValidPassword(String password) {
        if (password == null || !password.matches(PASSWORD_PATTERN)) {
            throw new BizException("Password " + PASSWORD_MESSAGE);
        }
    }

    public static boolean isValidStatus(String status) {
        return "ACTIVE".equals(status) || "INACTIVE".equals(status);
    }
}
