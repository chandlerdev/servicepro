package com.servicepros.auth;

import com.servicepros.common.ApiException;
import com.servicepros.technician.Technician;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Technician app logins (admins are managed by the Super Admin, see SuperAdminService). */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates or updates the login of a technician (email = technician email, name = technician name).
     * {@code password} null keeps the current password; a new password signs the technician out everywhere.
     * Email uniqueness is checked by the caller.
     */
    @Transactional
    public void upsertTechnicianLogin(Technician tech, String password) {
        User u = users.findByTechnicianId(tech.getId()).orElseGet(User::new);
        if (u.getId() == null && password == null) {
            throw ApiException.badRequest("Set a password to create this technician's login");
        }
        u.setName(tech.getName());
        u.setEmail(tech.getEmail());
        u.setRole(User.Role.TECHNICIAN);
        u.setTechnician(tech);
        if (password != null) {
            boolean existing = u.getId() != null;
            u.setPasswordHash(passwordEncoder.encode(password));
            users.save(u);
            if (existing) {
                refreshTokens.revokeAllForUser(u.getId());
            }
        } else {
            users.save(u);
        }
    }
}
