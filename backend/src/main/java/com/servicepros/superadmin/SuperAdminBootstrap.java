package com.servicepros.superadmin;

import com.servicepros.auth.User;
import com.servicepros.auth.UserRepository;
import com.servicepros.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the first Super Admin login (from SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD) when none exists,
 * so a fresh database can be logged into. Does nothing once a Super Admin exists.
 */
@Slf4j
@Order(0)
@Component
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(User.Role.SUPER_ADMIN)) return;
        AppProperties.SuperAdmin cfg = props.getSuperAdmin();
        if (!StringUtils.hasText(cfg.getEmail()) || !StringUtils.hasText(cfg.getPassword())) {
            log.warn("No Super Admin exists and SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD are not set — nobody can log in");
            return;
        }
        String email = cfg.getEmail().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            log.warn("Cannot create Super Admin: {} is already used by another account", email);
            return;
        }
        User u = new User();
        u.setRole(User.Role.SUPER_ADMIN);
        u.setName("Super Admin");
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(cfg.getPassword()));
        users.save(u);
        log.info("Created Super Admin login {}", email);
    }
}
