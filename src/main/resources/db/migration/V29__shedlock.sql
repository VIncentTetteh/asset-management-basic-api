-- ShedLock coordination table.
--
-- Spring's @Scheduled fires independently inside every JVM, so a second replica
-- means every cron runs twice. That is not merely wasteful here: the monthly
-- depreciation batch posts financial entries, the dunning job emails paying
-- customers and downgrades plans, and the account purge job hard-deletes tenants.
-- ShedLock makes each execution take a row lock keyed by job name, so exactly one
-- instance proceeds and the others skip that tick.
--
-- Column names and types are fixed by ShedLock's JdbcTemplateLockProvider — do not
-- rename them. TIMESTAMP is stored in UTC; the provider writes with an explicit
-- clock so instances with skewed clocks cannot steal a live lock.

CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT shedlock_pkey PRIMARY KEY (name)
);

COMMENT ON TABLE shedlock IS
    'ShedLock job coordination. One row per @SchedulerLock name; presence of a row with '
    'lock_until in the future means another instance holds that job. Safe to delete rows '
    'only when no instance is running.';
