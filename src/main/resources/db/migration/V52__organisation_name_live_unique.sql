-- V52: organisation name unique among live tenants, case-insensitively; contact
-- email no longer unique across tenants.
--
-- 1. Name. V7 declared organisation.name UNIQUE: case-sensitive ("Acme" and "ACME"
--    were both accepted, though the service refuses them) and counting closed
--    tenants (deleted_at set by account closure), so a closed tenant's name could
--    never be reused and the failure read as a duplicate. It becomes a unique index
--    on lower(name) WHERE deleted_at IS NULL. Closure is the only "closed" state:
--    OrganisationStatus.DELETED is never set, and SUSPENDED/INACTIVE tenants keep
--    their name. Restoring a closed tenant whose name is now taken is refused by
--    AccountLifecycleService.cancelClosure.
-- 2. Contact email. V7 made it globally UNIQUE, so two tenants sharing a mailbox
--    got 409 "already exists" and learnt another tenant uses that address. The
--    constraint is dropped; nothing replaces it (a contact mailbox is not an identity).
--
-- Registration number and tax id keep their V7 global uniqueness. The service now
-- stores a cleared value as NULL instead of "" (NULLs are distinct in Postgres);
-- existing "" rows are left untouched.
--
-- Pre-check: fails loudly and rolls back (one transaction) when live tenants share a
-- name case-insensitively. No data is changed or deleted. Run beforehand to know:
--   SELECT lower(name), count(*) FROM organisation
--    WHERE deleted_at IS NULL GROUP BY 1 HAVING count(*) > 1;
--
-- The V7 constraints were created inline, so their names are Postgres defaults
-- (organisation_name_key, organisation_contact_email_key) on a Flyway-built schema,
-- but may differ where Hibernate once created the table. They are found by column
-- instead of by name. The new index is created before the name constraint is
-- dropped, so live-name uniqueness is never unenforced.

DO $$
DECLARE
    dup_groups bigint;
BEGIN
    SELECT count(*) INTO dup_groups FROM (
        SELECT 1 FROM organisation WHERE deleted_at IS NULL
         GROUP BY lower(name) HAVING count(*) > 1) d;
    IF dup_groups > 0 THEN
        RAISE EXCEPTION 'V52: % group(s) of live organisations share a name (case-insensitive). Rename them (this migration changes no data) and redeploy.',
            dup_groups;
    END IF;
END;
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_organisation_name_live
    ON organisation (lower(name))
    WHERE deleted_at IS NULL;

DO $$
DECLARE
    con record;
BEGIN
    FOR con IN
        SELECT c.conname
          FROM pg_constraint c
          JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
         WHERE c.conrelid = 'organisation'::regclass
           AND c.contype = 'u'
           AND array_length(c.conkey, 1) = 1
           AND a.attname IN ('name', 'contact_email')
    LOOP
        EXECUTE format('ALTER TABLE organisation DROP CONSTRAINT %I', con.conname);
    END LOOP;
END;
$$;
