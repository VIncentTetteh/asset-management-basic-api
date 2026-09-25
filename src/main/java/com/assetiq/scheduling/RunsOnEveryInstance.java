package com.assetiq.scheduling;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @Scheduled} method that is <em>deliberately</em> left unlocked because
 * it must run on every replica.
 *
 * <p>Almost every scheduled job in this application does shared work — it mutates the
 * database, emails customers, or calls out to third parties — and must therefore run on
 * exactly one instance, enforced with
 * {@link net.javacrumbs.shedlock.spring.annotation.SchedulerLock}. A small number do the
 * opposite: they refresh state held <em>inside</em> a single JVM, so locking them would
 * mean one instance refreshes and every other instance keeps stale state forever. That
 * failure is silent and would be very hard to trace back to a lock annotation.
 *
 * <p>This annotation exists so that distinction is explicit and enforceable rather than
 * inferred from an absent annotation. {@code ScheduledJobLockingTest} requires every
 * {@code @Scheduled} method to carry either {@code @SchedulerLock} or this, so a newly
 * added job cannot silently default to running everywhere.
 *
 * <p><b>Before using this, verify the job touches no shared state at all.</b> If it
 * writes to the database, sends mail, or calls an external system, it needs a lock.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RunsOnEveryInstance {

    /** Why running on every replica is correct — and safe — for this particular job. */
    String reason();
}
