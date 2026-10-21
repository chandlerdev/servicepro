package com.servicepros.security;

import com.servicepros.auth.User;

/**
 * What we know about the caller from the access token (no DB hit per request).
 *
 * @param ownerId the Admin whose data this user works with: the admin's own id for ADMIN,
 *                the technician's admin for TECHNICIAN, null for SUPER_ADMIN
 */
public record AppUserPrincipal(Long id, String email, User.Role role, Long technicianId, Long ownerId) {

    public boolean isAdmin() {
        return role == User.Role.ADMIN;
    }

    public boolean isSuperAdmin() {
        return role == User.Role.SUPER_ADMIN;
    }
}
