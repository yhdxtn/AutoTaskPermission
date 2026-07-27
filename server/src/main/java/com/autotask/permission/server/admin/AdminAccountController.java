package com.autotask.permission.server.admin;

import java.time.LocalDateTime;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/account")
public class AdminAccountController {

    private final AdminUserRepository repository;

    public AdminAccountController(AdminUserRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/me")
    AdminAccountResponse me(Authentication authentication) {
        AdminUser user = repository.findByUsername(authentication.getName())
            .orElseThrow(() -> new IllegalArgumentException("管理员账号不存在"));
        return new AdminAccountResponse(
            user.getUsername(),
            user.isEnabled(),
            user.isExpired(),
            user.getExpiresAt()
        );
    }

    record AdminAccountResponse(
        String username,
        boolean enabled,
        boolean expired,
        LocalDateTime expiresAt
    ) {
    }
}
