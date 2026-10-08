-- =============================================================================
-- V3: licences and seats (Epic T, story T1; documents/teams-and-licences-plan.md).
--
-- A company holds one licence with a fixed number of seats. Owners, admins,
-- builders and electricians use a seat; viewers never do. Active members and
-- pending invitations both count, so a company cannot invite past its limit.
-- The limit is enforced here, by trigger, whatever the code path.
--
-- A trial is 3 seats for 14 days. Data is kept 90 days after a licence closes.
-- =============================================================================

ALTER TABLE electriplan.organisation
    ADD COLUMN seat_limit        integer NOT NULL DEFAULT 3 CHECK (seat_limit >= 1),
    ADD COLUMN licence_starts_on date    NOT NULL DEFAULT current_date,
    ADD COLUMN licence_ends_on   date,
    ADD COLUMN closed_at         timestamptz;

-- A company closed before this migration closed when it was last changed.
UPDATE electriplan.organisation SET closed_at = updated_at WHERE status = 'closed' AND closed_at IS NULL;

ALTER TABLE electriplan.organisation
    ADD CONSTRAINT organisation_licence_period CHECK (licence_ends_on IS NULL OR licence_ends_on >= licence_starts_on),
    ADD CONSTRAINT organisation_closed_at CHECK ((status = 'closed') = (closed_at IS NOT NULL));

COMMENT ON COLUMN electriplan.organisation.seat_limit IS
    'People the licence covers. Owners, admins, builders and electricians use a seat (active members and pending invitations); viewers never do. Set by the operator; a trial has 3.';
COMMENT ON COLUMN electriplan.organisation.licence_starts_on IS 'First day of the licence period.';
COMMENT ON COLUMN electriplan.organisation.licence_ends_on IS
    'Last day of the licence period, after which the company becomes read-only (suspended). NULL: no end date. A trial ends 14 days after it starts.';
COMMENT ON COLUMN electriplan.organisation.closed_at IS 'When the licence was closed. The company''s data is deleted 90 days later.';

-- A new trial runs for 14 days unless an end date is given.
CREATE FUNCTION electriplan.organisation_trial_period() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status = 'trial' AND NEW.licence_ends_on IS NULL THEN
        NEW.licence_ends_on := NEW.licence_starts_on + 14;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER organisation_trial_period BEFORE INSERT ON electriplan.organisation
    FOR EACH ROW EXECUTE FUNCTION electriplan.organisation_trial_period();

-- -----------------------------------------------------------------------------
-- Seats
-- -----------------------------------------------------------------------------

CREATE FUNCTION electriplan.is_seated_role(p_role text) RETURNS boolean
    LANGUAGE sql IMMUTABLE
AS $$ SELECT p_role IN ('owner', 'admin', 'builder', 'electrician') $$;

COMMENT ON FUNCTION electriplan.is_seated_role(text) IS 'Whether a role uses a seat. Viewers do not.';

CREATE FUNCTION electriplan.seats_in_use(p_organisation uuid) RETURNS integer
    LANGUAGE sql STABLE
AS $$
    SELECT (SELECT count(*) FROM electriplan.organisation_member m
             WHERE m.organisation_id = p_organisation
               AND m.status = 'active'
               AND electriplan.is_seated_role(m.role))
         + (SELECT count(*) FROM electriplan.organisation_invitation i
             WHERE i.organisation_id = p_organisation
               AND i.accepted_at IS NULL AND i.revoked_at IS NULL AND i.expires_at > now()
               AND electriplan.is_seated_role(i.role))
$$;

COMMENT ON FUNCTION electriplan.seats_in_use(uuid) IS
    'Seats a company is using: active members and pending (not accepted, revoked or expired) invitations in a seated role.';

-- Refuses a member or invitation that would take the company past its seat
-- limit. Only a change that takes a new seat is checked: adding a seated
-- member or invitation, promoting a viewer, reactivating a suspended member.
-- Freeing seats is always allowed, and so is lowering the limit below current
-- use (which then blocks new seats until the company is back under it).
--
-- Accepting an invitation: mark the invitation accepted first, then add the
-- member, in one transaction, so the seat is not counted twice.
CREATE FUNCTION electriplan.enforce_seat_limit() RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    v_takes_seat boolean;
    v_had_seat   boolean := false;
    v_limit      integer;
    v_in_use     integer;
BEGIN
    IF TG_TABLE_NAME = 'organisation_member' THEN
        v_takes_seat := NEW.status = 'active' AND electriplan.is_seated_role(NEW.role);
        IF TG_OP = 'UPDATE' THEN
            v_had_seat := OLD.status = 'active' AND electriplan.is_seated_role(OLD.role);
        END IF;
    ELSE
        v_takes_seat := NEW.accepted_at IS NULL AND NEW.revoked_at IS NULL AND NEW.expires_at > now()
                        AND electriplan.is_seated_role(NEW.role);
        IF TG_OP = 'UPDATE' THEN
            v_had_seat := OLD.accepted_at IS NULL AND OLD.revoked_at IS NULL AND OLD.expires_at > now()
                          AND electriplan.is_seated_role(OLD.role);
        END IF;
    END IF;

    IF NOT v_takes_seat OR v_had_seat THEN
        RETURN NEW;
    END IF;

    -- One seat change at a time per company, so two requests cannot both take
    -- the last seat.
    SELECT seat_limit INTO v_limit FROM electriplan.organisation WHERE id = NEW.organisation_id FOR UPDATE;
    v_in_use := electriplan.seats_in_use(NEW.organisation_id);

    IF v_in_use + 1 > v_limit THEN
        RAISE EXCEPTION 'No free seat: the licence covers % %, all in use',
                v_limit, CASE WHEN v_limit = 1 THEN 'seat' ELSE 'seats' END
            USING ERRCODE = 'check_violation',
                  HINT = 'Free a seat (remove or suspend a member, revoke an invitation, or make someone a viewer), or ask for more seats.';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER organisation_member_seats BEFORE INSERT OR UPDATE OF role, status ON electriplan.organisation_member
    FOR EACH ROW EXECUTE FUNCTION electriplan.enforce_seat_limit();
CREATE TRIGGER organisation_invitation_seats BEFORE INSERT OR UPDATE OF role, accepted_at, revoked_at, expires_at
    ON electriplan.organisation_invitation
    FOR EACH ROW EXECUTE FUNCTION electriplan.enforce_seat_limit();
