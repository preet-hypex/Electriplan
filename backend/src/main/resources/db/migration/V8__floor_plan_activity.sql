-- =============================================================================
-- V8: saving a floor plan is activity on its house (Epic P, story P4).
--
-- A house's updated_at (and so its project's last_activity_at, V7) moves when
-- any of its floor-plan versions is created or changed, so "continue where you
-- left off" and the project list show the work people are actually doing.
-- =============================================================================

CREATE FUNCTION electriplan.floor_plan_touch_house() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE electriplan.plan h
       SET updated_at = now()
      FROM electriplan.plan_level l
     WHERE l.organisation_id = NEW.organisation_id AND l.id = NEW.plan_level_id
       AND h.organisation_id = l.organisation_id AND h.id = l.plan_id;
    RETURN NULL;
END
$$;

CREATE TRIGGER floor_plan_touch_house AFTER INSERT OR UPDATE ON electriplan.floor_plan_version
    FOR EACH ROW EXECUTE FUNCTION electriplan.floor_plan_touch_house();

COMMENT ON FUNCTION electriplan.floor_plan_touch_house() IS
    'Saving a floor plan moves its house''s updated_at, and through it its project''s last_activity_at.';
