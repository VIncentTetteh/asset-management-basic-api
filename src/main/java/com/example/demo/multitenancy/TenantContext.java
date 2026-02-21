package com.example.demo.multitenancy;

import java.util.UUID;

/**
 * Simple ThreadLocal holder for the current request's organisation (tenant) id.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setOrganisationId(UUID id) {
        CURRENT.set(id);
    }

    public static UUID getOrganisationId() {
        return CURRENT.get();
    }

    public static boolean hasOrganisationId() {
        return CURRENT.get() != null;
    }

    public static void clear() {
        CURRENT.remove();
    }
}

