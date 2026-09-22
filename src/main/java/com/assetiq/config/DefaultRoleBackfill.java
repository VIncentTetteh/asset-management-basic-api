package com.assetiq.config;

import com.assetiq.models.Organisation;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.services.DefaultRoleSeederService;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Startup backfill: gives every existing organisation the standard role set it
 * is missing. Tenants that self-registered before registration seeded these
 * roles only have ADMIN and USER.
 *
 * <p>Safe by construction: {@link DefaultRoleSeederService#addMissingRoles} only
 * creates roles whose name the organisation does not already use, and never
 * touches an existing role, so custom or edited roles are left alone and a
 * second run creates nothing. The run holds a ShedLock lock so replicas starting
 * together do not race to create the same role twice, and each organisation is
 * its own transaction so one failure does not undo the rest.
 */
@Component
public class DefaultRoleBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultRoleBackfill.class);
    static final String LOCK_NAME = "defaultRoleBackfill";

    private final OrganisationRepository organisationRepository;
    private final DefaultRoleSeederService seeder;
    private final LockProvider lockProvider;

    public DefaultRoleBackfill(OrganisationRepository organisationRepository,
                               DefaultRoleSeederService seeder,
                               LockProvider lockProvider) {
        this.organisationRepository = organisationRepository;
        this.seeder = seeder;
        this.lockProvider = lockProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        new DefaultLockingTaskExecutor(lockProvider).executeWithLock((Runnable) this::backfill,
                new LockConfiguration(Instant.now(), LOCK_NAME, Duration.ofMinutes(10), Duration.ZERO));
    }

    /** Adds missing standard roles to every live organisation; returns how many were created. */
    int backfill() {
        int created = 0;
        for (Organisation org : organisationRepository.findAllByDeletedAtIsNull()) {
            try {
                created += seeder.addMissingRoles(org);
            } catch (RuntimeException e) {
                log.warn("[ROLE BACKFILL] Could not add standard roles to organisation {}: {}", org.getId(), e.getMessage());
            }
        }
        if (created > 0) {
            log.info("[ROLE BACKFILL] Created {} missing standard role(s)", created);
        }
        return created;
    }
}
