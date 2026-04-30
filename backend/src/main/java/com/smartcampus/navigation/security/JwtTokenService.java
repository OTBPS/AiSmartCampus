package com.smartcampus.navigation.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcampus.navigation.user.UserEntity;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final String secret;
    private final long ttlMinutes;
    private final ObjectMapper objectMapper;

    public JwtTokenService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.ttl-minutes}") long ttlMinutes,
            ObjectMapper objectMapper
    ) {
        this.secret = secret;
        this.ttlMinutes = ttlMinutes;
        this.objectMapper = objectMapper;
    }

    public String generate(UserEntity user) {
        try {
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", user.username);
            payload.put("uid", user.id);
            payload.put("role", user.role);
            payload.put("name", user.displayName);
            payload.put("exp", Instant.now().plusSeconds(ttlMinutes * 60).getEpochSecond());
            String headerPart = encode(objectMapper.writeValueAsBytes(header));
            String payloadPart = encode(objectMapper.writeValueAsBytes(payload));
            String body = headerPart + "." + payloadPart;
            return body + "." + sign(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate token", ex);
        }
    }

    public TokenUser parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String body = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(body), parts[2])) {
                return null;
            }
            byte[] json = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(json, new TypeReference<>() {});
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > exp) {
                return null;
            }
            Long userId = ((Number) payload.get("uid")).longValue();
            String username = String.valueOf(payload.get("sub"));
            String role = String.valueOf(payload.get("role"));
            String displayName = String.valueOf(payload.get("name"));
            return new TokenUser(userId, username, displayName, role);
        } catch (Exception ex) {
            return null;
        }
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return encode(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    public record TokenUser(Long id, String username, String displayName, String role) {
    }
}

