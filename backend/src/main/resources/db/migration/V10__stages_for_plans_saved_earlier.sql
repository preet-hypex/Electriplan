-- =============================================================================
-- V10: houses whose floor plan was saved before their stage followed the work
-- (Epic P, story P7).
--
-- Since P7 a house moves to floor_plan_review when its floor plan is first
-- saved. A house saved before that is still "awaiting upload" although it has
-- a plan. Each such house moves now, recorded in its stage history with no
-- actor (the system) and a note saying why.
--
-- Row-level security shows each company only its own rows, so the move is
-- made company by company, as the application would.
-- =============================================================================

DO $$
DECLARE
    v_org uuid;
BEGIN
    PERFORM set_config('electriplan.stage_note', 'Its floor plan was saved before stages followed the work', true);
    FOR v_org IN SELECT DISTINCT organisation_id FROM electriplan.plan WHERE stage = 'awaiting_upload' LOOP
        PERFORM set_config('electriplan.organisation_id', v_org::text, true);
        UPDATE electriplan.plan h
           SET stage = 'floor_plan_review'
         WHERE h.organisation_id = v_org
           AND h.stage = 'awaiting_upload'
           AND EXISTS (SELECT 1
                         FROM electriplan.plan_level l
                         JOIN electriplan.floor_plan_version v ON v.plan_level_id = l.id
                        WHERE l.plan_id = h.id);
    END LOOP;
    PERFORM set_config('electriplan.organisation_id', '', true);
    PERFORM set_config('electriplan.stage_note', '', true);
END
$$;
