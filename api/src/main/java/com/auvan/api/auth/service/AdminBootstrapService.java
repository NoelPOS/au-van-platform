package com.auvan.api.auth.service;

import com.auvan.api.auth.config.AuthProperties;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBootstrapService {
    private final AppUserRepository users;
    private final AuthProperties properties;

    public AdminBootstrapService(AppUserRepository users, AuthProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Transactional
    public void bootstrap() {
        String lineSubject = properties.adminBootstrap().lineSubject();
        if (lineSubject == null || lineSubject.isBlank()) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_LINE_SUBJECT must be configured before bootstrapping an administrator.");
        }

        AppUser user = users.findByLineSubject(lineSubject)
                .orElseGet(() -> new AppUser(lineSubject, null));
        user.promoteToAdmin();
        users.save(user);
    }
}
