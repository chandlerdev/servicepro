package com.servicepros.tenant;

import java.util.function.Supplier;

/**
 * Explicit tenant override for code that runs without a logged-in admin/technician
 * (startup tasks, webhooks, tests). Normally the tenant comes from the access token.
 *
 * A "tenant" is an Admin: every business record belongs to exactly one admin (owner_id).
 */
public final class TenantContext {

    /** Tenant id meaning "no filter" — used by the Super Admin and system code. */
    public static final Long ROOT = 0L;

    private static final ThreadLocal<Long> OVERRIDE = new ThreadLocal<>();

    private TenantContext() {
    }

    static Long override() {
        return OVERRIDE.get();
    }

    /** Runs {@code work} as if the given admin were logged in. */
    public static <T> T callAs(Long ownerId, Supplier<T> work) {
        Long previous = OVERRIDE.get();
        OVERRIDE.set(ownerId);
        try {
            return work.get();
        } finally {
            if (previous == null) OVERRIDE.remove(); else OVERRIDE.set(previous);
        }
    }

    public static void runAs(Long ownerId, Runnable work) {
        callAs(ownerId, () -> {
            work.run();
            return null;
        });
    }
}
