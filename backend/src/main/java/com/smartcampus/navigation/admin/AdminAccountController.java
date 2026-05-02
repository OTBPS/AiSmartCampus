package com.smartcampus.navigation.admin;

import com.smartcampus.navigation.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {
    private final AdminAccountService accountService;

    public AdminAccountController(AdminAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ApiResponse<List<AdminAccountResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(accountService.list(keyword, status));
    }

    @PostMapping
    public ApiResponse<AdminAccountResponse> create(@Valid @RequestBody AdminAccountCreateRequest request) {
        return ApiResponse.ok(accountService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminAccountResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody AdminAccountUpdateRequest request
    ) {
        return ApiResponse.ok(accountService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<AdminAccountResponse> deactivate(@PathVariable Long id) {
        return ApiResponse.ok(accountService.deactivate(id));
    }
}
