package com.autotask.permission.server.admin;

import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminUserInitializer implements CommandLineRunner {

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final Integer validDays;

    public AdminUserInitializer(
        AdminUserRepository repository,
        PasswordEncoder passwordEncoder,
        @Value("${autotask.admin.username}") String username,
        @Value("${autotask.admin.password}") String password,
        @Value("${autotask.admin.valid-days:365}") Integer validDays
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.validDays = validDays;
    }

    @Override
    public void run(String... args) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalStateException("默认管理员账号或密码不能为空");
        }
        if (repository.existsByUsername(username)) {
            return;
        }

        AdminUser admin = new AdminUser();
        admin.setUsername(username.trim());
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setEnabled(true);
        if (validDays != null && validDays > 0) {
            admin.setExpiresAt(LocalDateTime.now().plusDays(validDays));
        }
        repository.save(admin);
    }
}
