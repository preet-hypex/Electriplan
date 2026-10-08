-- =============================================================================
-- V7: what the projects workspace needs from the database (Epic P, story P1;
-- documents/projects-workspace-plan.md).
--
--   1. A project's reference (PRJ-000042) is assigned here, on insert, from
--      the company's counter: gapless per company, whatever the code path.
--   2. last_activity_at: when anything in the project last changed (the
--      project or one of its houses), so lists can show newest work first.
--   3. lock_version on project, for optimistic locking (plan already has it).
--   4. A project's state is always chosen: no default state.
-- =============================================================================

-- 1. References -----------------------------------------------------------------

CREATE FUNCTION electriplan.project_assign_reference() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.reference IS NULL THEN
        NEW.reference := electriplan.next_reference(NEW.organisation_id, 'project', 'PRJ');
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER project_assign_reference BEFORE INSERT ON electriplan.project
    FOR EACH ROW EXECUTE FUNCTION electriplan.project_assign_reference();

COMMENT ON FUNCTION electriplan.project_assign_reference() IS
    'Gives a new project the company''s next reference (PRJ-000001...) unless one is supplied.';

-- 2. Last activity --------------------------------------------------------------

ALTER TABLE electriplan.project
    ADD COLUMN last_activity_at timestamptz NOT NULL DEFAULT now();

UPDATE electriplan.project p
   SET last_activity_at = greatest(p.updated_at,
                                   coalesce((SELECT max(h.updated_at) FROM electriplan.plan h WHERE h.project_id = p.id),
                                            p.updated_at));

COMMENT ON COLUMN electriplan.project.last_activity_at IS
    'When the project or any of its houses last changed: the order of the project list.';

CREATE FUNCTION electriplan.project_touch_activity() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.last_activity_at := now();
    RETURN NEW;
END
$$;

CREATE TRIGGER project_touch_activity BEFORE UPDATE ON electriplan.project
    FOR EACH ROW EXECUTE FUNCTION electriplan.project_touch_activity();

-- A house added or changed is activity in its project.
CREATE FUNCTION electriplan.plan_touch_project() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE electriplan.project SET last_activity_at = now()
     WHERE organisation_id = NEW.organisation_id AND id = NEW.project_id;
    RETURN NULL;
END
$$;

CREATE TRIGGER plan_touch_project AFTER INSERT OR UPDATE ON electriplan.plan
    FOR EACH ROW EXECUTE FUNCTION electriplan.plan_touch_project();

CREATE INDEX ix_project_activity ON electriplan.project (organisation_id, last_activity_at DESC);

-- 3. Optimistic locking ------------------------------------------------------------

ALTER TABLE electriplan.project ADD COLUMN lock_version integer NOT NULL DEFAULT 0;

COMMENT ON COLUMN electriplan.project.lock_version IS
    'Optimistic locking: an update must name the version it read; the API refuses a stale one.';

-- 4. No default state --------------------------------------------------------------

ALTER TABLE electriplan.project ALTER COLUMN site_state DROP DEFAULT;
