package com.autotask.permission.server.admin;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseAdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository repository;

    public DatabaseAdminUserDetailsService(AdminUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AdminUser admin = repository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("管理员账号不存在"));

        return User.withUsername(admin.getUsername())
            .password(admin.getPasswordHash())
            .roles("ADMIN")
            .disabled(!admin.isEnabled())
            .accountExpired(admin.isExpired())
            .build();
    }
}
