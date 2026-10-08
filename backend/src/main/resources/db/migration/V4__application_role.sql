-- =============================================================================
-- V4: the role the application runs as (Epic T, story T2).
--
-- Until now the API connected as the owner of the tables, a superuser, which
-- row-level security does not apply to. From here on:
--   * the owner (whoever runs this migration, `electriplan` locally) owns the
--     schema and runs Flyway, and nothing else;
--   * electriplan_app is what the API works as: not a superuser, cannot bypass
--     row-level security, cannot create, alter or drop anything, and gets only
--     the access each table needs.
--
-- electriplan_app cannot log in. The API logs in as electriplan_api, a member
-- of it, created with its password by infra/postgres/application-login.sql
-- (passwords do not belong in migrations).
-- =============================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'electriplan_app') THEN
        CREATE ROLE electriplan_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
END
$$;

COMMENT ON ROLE electriplan_app IS
    'What the Electriplan API works as: data access only, row-level security always applies. The API logs in as a member (electriplan_api).';

GRANT USAGE ON SCHEMA electriplan TO electriplan_app;

-- Data access to every table, then narrowed below.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA electriplan TO electriplan_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA electriplan TO electriplan_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA electriplan TO electriplan_app;

-- Flyway's own bookkeeping is the owner's alone.
REVOKE ALL ON electriplan.flyway_schema_history FROM electriplan_app;

-- Reference data is seeded by migrations and only read by the application.
REVOKE INSERT, UPDATE, DELETE ON electriplan.plan_stage, electriplan.plan_stage_transition,
    electriplan.electricity_distributor FROM electriplan_app;

-- History is append-only: written, never rewritten or removed.
REVOKE UPDATE, DELETE ON electriplan.plan_stage_event, electriplan.audit_event FROM electriplan_app;

-- Tables and sequences later migrations create get the same data access, so
-- no migration can forget its grants. (Any narrowing is done table by table,
-- as above.)
ALTER DEFAULT PRIVILEGES IN SCHEMA electriplan
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO electriplan_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA electriplan
    GRANT USAGE, SELECT ON SEQUENCES TO electriplan_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA electriplan
    GRANT EXECUTE ON FUNCTIONS TO electriplan_app;
