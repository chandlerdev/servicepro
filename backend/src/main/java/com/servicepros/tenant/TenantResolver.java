package com.servicepros.tenant;

import java.util.Map;

import com.servicepros.auth.User;
import com.servicepros.security.AppUserPrincipal;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which admin's data the current request may see. Hibernate then
 * filters every query on {@code owner_id} and stamps new rows with it (see {@link TenantEntity}).
 *
 * <ul>
 *   <li>Admin → their own id</li>
 *   <li>Technician → the id of the admin they belong to</li>
 *   <li>Super Admin, startup tasks, webhooks, login → {@link TenantContext#ROOT} (no filter)</li>
 * </ul>
 */
@Component
public class TenantResolver implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long override = TenantContext.override();
        if (override != null) return override;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p
                && p.role() != User.Role.SUPER_ADMIN && p.ownerId() != null) {
            return p.ownerId();
        }
        return TenantContext.ROOT;
    }

    @Override
    public boolean isRoot(Long tenantId) {
        return TenantContext.ROOT.equals(tenantId);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
