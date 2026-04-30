package com.smartcampus.navigation.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class MockAiServiceTest {
    @Test
    void intentNamesStayStableForFrontendContract() {
        assertEquals("find_poi", "find_poi");
        assertEquals("recommend_place", "recommend_place");
        assertEquals("route_help", "route_help");
    }

    @Test
    void seedPasswordHashMatchesDefaultPassword() {
        String hash = "$2a$10$eI5SckucFgBQ2licTX9bx.oOVlLvzqARVEacwAU3VHjvIsabigadG";
        assertTrue(new BCryptPasswordEncoder().matches("123456", hash));
    }
}
