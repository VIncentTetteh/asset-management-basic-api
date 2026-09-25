package com.assetiq.scheduling;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@code @Scheduled} method must declare how it behaves on more than one replica.
 *
 * <p><b>Why this exists.</b> Spring's scheduler is per-JVM: a second replica runs every
 * cron a second time. This application had ten scheduled jobs and no locking of any kind,
 * which meant it could only ever run on one instance — no HA, no rolling deploys — and
 * the failure mode if anyone scaled it was silent and expensive. The monthly depreciation
 * batch would post duplicate financial entries, the dunning job would email paying
 * customers twice and re-evaluate downgrades, {@code AccountPurgeJob} would run concurrent
 * hard deletes of tenant data, and webhook retries would double-deliver to customers.
 *
 * <p>The dangerous property of that bug is that nothing surfaces it. A missing
 * {@code @SchedulerLock} looks exactly like a job that does not need one, and the damage
 * only appears in production under scale, in data rather than in logs. So the choice is
 * made explicit and mandatory: a scheduled method carries either
 * {@link SchedulerLock} or {@link RunsOnEveryInstance}, and adding a new job without
 * deciding fails the build.
 *
 * <p>This inspects compiled classes rather than source so it sees the annotations exactly
 * as the runtime does — including any that a refactor moved onto an inherited method.
 */
@DisplayName("Scheduled jobs declare their multi-instance behaviour")
class ScheduledJobLockingTest {

    private static final Path CLASS_ROOT = Path.of("target", "classes");
    private static final String BASE_PACKAGE = "com.assetiq";

    @Test
    @DisplayName("every @Scheduled method is either locked or explicitly exempt")
    void everyScheduledMethodDeclaresItsMultiInstanceBehaviour() throws Exception {
        List<String> undeclared = new ArrayList<>();
        int scheduledFound = 0;

        for (Class<?> type : applicationClasses()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Scheduled.class)) {
                    continue;
                }
                scheduledFound++;

                boolean locked = method.isAnnotationPresent(SchedulerLock.class);
                boolean exempt = method.isAnnotationPresent(RunsOnEveryInstance.class);

                if (locked && exempt) {
                    undeclared.add(type.getSimpleName() + "#" + method.getName()
                            + " (has BOTH @SchedulerLock and @RunsOnEveryInstance — pick one)");
                } else if (!locked && !exempt) {
                    undeclared.add(type.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(scheduledFound)
                .describedAs("Found no @Scheduled methods under %s — the scan is broken, "
                        + "so the assertion below would pass without checking anything", CLASS_ROOT)
                .isGreaterThan(0);

        assertThat(undeclared)
                .describedAs("""
                        These @Scheduled methods do not say what should happen when the \
                        application runs on more than one replica, so they would run once per \
                        instance. Add @SchedulerLock(name = "...", lockAtMostFor = "...", \
                        lockAtLeastFor = "...") if the job does shared work — writes to the \
                        database, sends email, calls a third party. Add \
                        @RunsOnEveryInstance(reason = "...") ONLY if it refreshes state held \
                        inside this JVM, where locking would leave other replicas stale.""")
                .isEmpty();
    }

    @Test
    @DisplayName("every @SchedulerLock name is unique")
    void lockNamesAreUnique() throws Exception {
        List<String> names = new ArrayList<>();
        for (Class<?> type : applicationClasses()) {
            for (Method method : type.getDeclaredMethods()) {
                SchedulerLock lock = method.getAnnotation(SchedulerLock.class);
                if (lock != null) {
                    names.add(lock.name());
                }
            }
        }

        // Two jobs sharing a lock name would silently block each other: whichever ran
        // first would hold the row and the other would skip its tick entirely.
        assertThat(names)
                .describedAs("@SchedulerLock names key a shared table row, so a duplicate "
                        + "means two unrelated jobs suppress one another")
                .doesNotHaveDuplicates()
                .isNotEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static List<Class<?>> applicationClasses() throws IOException {
        try (Stream<Path> paths = Files.walk(CLASS_ROOT)) {
            List<Class<?>> classes = new ArrayList<>();
            for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                String className = CLASS_ROOT.relativize(path).toString()
                        .replace(java.io.File.separatorChar, '.')
                        .replaceAll("\\.class$", "");
                if (!className.startsWith(BASE_PACKAGE)) {
                    continue;
                }
                try {
                    classes.add(Class.forName(className, false,
                            ScheduledJobLockingTest.class.getClassLoader()));
                } catch (Throwable ignored) {
                    // Classes whose optional dependencies are absent cannot be loaded for
                    // inspection; they cannot carry a live @Scheduled method either.
                }
            }
            return classes;
        }
    }
}
