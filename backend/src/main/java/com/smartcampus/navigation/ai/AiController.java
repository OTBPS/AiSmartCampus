package com.smartcampus.navigation.ai;

import com.smartcampus.navigation.common.ApiResponse;
import com.smartcampus.navigation.security.SecurityUsers;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final MockAiService mockAiService;

    public AiController(MockAiService mockAiService) {
        this.mockAiService = mockAiService;
    }

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ApiResponse.ok(mockAiService.chat(SecurityUsers.current().id(), request.message));
    }

    @GetMapping("/admin/logs")
    public ApiResponse<List<AiMessageEntity>> logs() {
        return ApiResponse.ok(mockAiService.logs());
    }
}

