-- =============================================================================
-- V5: inside a company, only that company (Epic T, story T3).
--
-- V2 let people always read their own memberships and the companies they
-- belong to, so the app can offer a company switcher. That also applied inside
-- a company: working in Acme, someone who also belongs to Bolt could read
-- Bolt's company row and their Bolt membership. Now those two rules apply only
-- when no company is set (the switcher's request); once a company is chosen,
-- every table shows that company and nothing else.
-- =============================================================================

ALTER POLICY own_memberships ON electriplan.organisation_member
    USING (electriplan.current_organisation_id() IS NULL
           AND user_id = electriplan.current_actor_id());

ALTER POLICY member_organisations ON electriplan.organisation
    USING (electriplan.current_organisation_id() IS NULL
           AND EXISTS (SELECT 1 FROM electriplan.organisation_member m
                        WHERE m.organisation_id = organisation.id
                          AND m.user_id = electriplan.current_actor_id()));
