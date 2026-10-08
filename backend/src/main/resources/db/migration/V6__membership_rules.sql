-- =============================================================================
-- V6: rules about memberships that hold whatever the role and whatever the
-- code path (Epic T, story T4; documents/teams-and-licences-plan.md §4).
--
--   1. Nobody changes their own role or status. (Leaving a company is allowed.)
--   2. Only an owner can change or remove an owner, or make someone an owner,
--      except when a company has no owner yet (its founding owner).
--   3. A company always keeps at least one active owner.
--
-- The acting person is electriplan.actor_id, which the API sets for every
-- transaction. With no actor (the operator's tools, migrations) rules 1 and 2
-- do not apply; rule 3 always does. Owner changes in a company are serialised
-- (row lock on the company), so two owners demoting each other at the same
-- moment cannot leave it with none.
-- =============================================================================

CREATE FUNCTION electriplan.protect_memberships() RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    v_org        uuid := COALESCE(NEW.organisation_id, OLD.organisation_id);
    v_actor      uuid := electriplan.current_actor_id();
    v_actor_role text;
    v_has_owner  boolean;
BEGIN
    -- The company itself is being deleted (this is its cascade): nothing to keep.
    IF TG_OP = 'DELETE' AND NOT EXISTS (SELECT 1 FROM electriplan.organisation WHERE id = OLD.organisation_id) THEN
        RETURN OLD;
    END IF;

    PERFORM 1 FROM electriplan.organisation WHERE id = v_org FOR UPDATE;

    SELECT EXISTS (SELECT 1 FROM electriplan.organisation_member
                    WHERE organisation_id = v_org AND role = 'owner' AND status = 'active')
      INTO v_has_owner;

    IF v_actor IS NOT NULL THEN
        -- 1. Not your own role or status.
        IF TG_OP = 'UPDATE' AND OLD.user_id = v_actor
           AND (NEW.role IS DISTINCT FROM OLD.role OR NEW.status IS DISTINCT FROM OLD.status) THEN
            RAISE EXCEPTION 'You cannot change your own role or status'
                USING ERRCODE = 'check_violation', HINT = 'Ask another owner or admin.';
        END IF;

        -- 2. Owners are managed by owners (a company's first owner excepted).
        SELECT role INTO v_actor_role FROM electriplan.organisation_member
         WHERE organisation_id = v_org AND user_id = v_actor AND status = 'active';
        IF v_actor_role IS DISTINCT FROM 'owner'
           AND ((TG_OP <> 'INSERT' AND OLD.role = 'owner' AND OLD.user_id <> v_actor)
                OR (TG_OP <> 'DELETE' AND NEW.role = 'owner' AND v_has_owner)) THEN
            RAISE EXCEPTION 'Only an owner can change or remove an owner, or make someone an owner'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    -- 3. Someone must stay an active owner.
    IF TG_OP <> 'INSERT' AND OLD.role = 'owner' AND OLD.status = 'active'
       AND (TG_OP = 'DELETE' OR NEW.role <> 'owner' OR NEW.status <> 'active')
       AND NOT EXISTS (SELECT 1 FROM electriplan.organisation_member
                        WHERE organisation_id = v_org AND user_id <> OLD.user_id
                          AND role = 'owner' AND status = 'active') THEN
        RAISE EXCEPTION 'A company must keep at least one active owner'
            USING ERRCODE = 'check_violation', HINT = 'Make someone else an owner first.';
    END IF;

    RETURN COALESCE(NEW, OLD);
END
$$;

CREATE TRIGGER organisation_member_rules BEFORE INSERT OR UPDATE OF role, status OR DELETE
    ON electriplan.organisation_member
    FOR EACH ROW EXECUTE FUNCTION electriplan.protect_memberships();

COMMENT ON FUNCTION electriplan.protect_memberships() IS
    'Membership rules for every role and code path: nobody changes their own role or status; only owners manage owners (a company''s first owner excepted); a company keeps an active owner.';
