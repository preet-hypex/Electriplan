-- The rules the core-domain schema (V2) promises, checked against Postgres.
-- Run by SchemaRulesPostgresTests as a non-superuser role (superusers bypass
-- row-level security), inside a transaction that is rolled back.
-- pg_temp.must_fail asserts a statement is refused; pg_temp.ok asserts a fact.
-- helper: assert a statement fails
CREATE OR REPLACE FUNCTION pg_temp.must_fail(sql text, label text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  BEGIN EXECUTE sql; EXCEPTION WHEN others THEN RAISE NOTICE 'OK  refused: % (%)', label, SQLERRM; RETURN; END;
  RAISE EXCEPTION 'FAIL expected to be refused: %', label;
END $$;
CREATE OR REPLACE FUNCTION pg_temp.ok(cond boolean, label text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN IF NOT cond THEN RAISE EXCEPTION 'FAIL %', label; END IF; RAISE NOTICE 'OK  %', label; END $$;

INSERT INTO electriplan.supabase_user (id, email, created_at) VALUES
  ('11111111-1111-4111-8111-111111111111', 'builder@a.com', now()),
  ('22222222-2222-4222-8222-222222222222', 'other@b.com', now()),
  ('33333333-3333-4333-8333-333333333333', 'sparky@a.com', now());

-- Organisation A, acting as its owner
SELECT set_config('electriplan.organisation_id', 'aaaaaaaa-0000-4000-8000-000000000001', false),
       set_config('electriplan.actor_id', '11111111-1111-4111-8111-111111111111', false);
INSERT INTO electriplan.organisation (id, name, slug) VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'Acme Homes', 'acme-homes');
INSERT INTO electriplan.organisation_member VALUES ('aaaaaaaa-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'owner');
INSERT INTO electriplan.organisation_member (organisation_id, user_id, role) VALUES ('aaaaaaaa-0000-4000-8000-000000000001', '33333333-3333-4333-8333-333333333333', 'electrician');
INSERT INTO electriplan.client (id, organisation_id, name) VALUES ('c0000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'Sam Lee');
INSERT INTO electriplan.project (id, organisation_id, reference, name, client_id, distributor_code)
  VALUES ('b0000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001',
          electriplan.next_reference('aaaaaaaa-0000-4000-8000-000000000001', 'project', 'PRJ'), '12 Example St', 'c0000000-0000-4000-8000-000000000001', 'citipower');
SELECT pg_temp.ok((SELECT reference FROM electriplan.project) = 'PRJ-000001', 'first project reference is PRJ-000001');
SELECT pg_temp.ok(electriplan.next_reference('aaaaaaaa-0000-4000-8000-000000000001', 'project', 'PRJ') = 'PRJ-000002', 'references count up');

INSERT INTO electriplan.plan (id, organisation_id, project_id, name) VALUES
  ('d0000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', 'Lot 12 Type A');
SELECT pg_temp.ok((SELECT count(*) FROM electriplan.plan_stage_event WHERE to_stage = 'awaiting_upload' AND from_stage IS NULL
                   AND actor_id = '11111111-1111-4111-8111-111111111111') = 1, 'creating a plan records its first stage and who');
SELECT pg_temp.must_fail($$UPDATE electriplan.plan SET stage = 'won'$$, 'jumping from awaiting_upload to won');
SELECT set_config('electriplan.stage_note', 'uploaded plan.png', false);
UPDATE electriplan.plan SET stage = 'analysing';
SELECT pg_temp.ok((SELECT note FROM electriplan.plan_stage_event WHERE to_stage = 'analysing') = 'uploaded plan.png', 'a valid move is recorded with its note');
UPDATE electriplan.plan SET stage = 'on_hold';
UPDATE electriplan.plan SET stage = 'awaiting_upload';
SELECT pg_temp.ok((SELECT count(*) FROM electriplan.plan_stage_event) = 4, 'pause and resume are recorded');

INSERT INTO electriplan.plan_level (id, organisation_id, plan_id, name, ordinal) VALUES
  ('e0000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001', 'Ground floor', 0);
INSERT INTO electriplan.stored_file (id, organisation_id, purpose, storage_backend, storage_key, content_type, byte_size, sha256)
  VALUES ('f0000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'floor_plan_source', 'local', 'a/plan.png', 'image/png', 1234, sha256('x'));
INSERT INTO electriplan.analysis_run (id, organisation_id, plan_level_id, source_file_id, status, started_at, finished_at)
  VALUES ('a1000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001', 'f0000000-0000-4000-8000-000000000001', 'succeeded', now(), now());
SELECT pg_temp.must_fail($$INSERT INTO electriplan.analysis_run (organisation_id, plan_level_id, source_file_id, status)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001', 'f0000000-0000-4000-8000-000000000001', 'failed')$$, 'a failed run without an error message');
INSERT INTO electriplan.floor_plan_version (id, organisation_id, plan_level_id, version_no, origin, analysis_run_id, document, room_count, floor_area_m2)
  VALUES ('f1000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001', 1, 'analysis', 'a1000000-0000-4000-8000-000000000001', '{"version":1}', 3, 19.86);
SELECT pg_temp.must_fail($$INSERT INTO electriplan.floor_plan_version (organisation_id, plan_level_id, version_no, origin, document)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001', 2, 'editor', '{}')$$, 'a second draft on the same level');
UPDATE electriplan.floor_plan_version SET document = '{"version":1,"walls":[]}' WHERE id = 'f1000000-0000-4000-8000-000000000001';
UPDATE electriplan.floor_plan_version SET state = 'committed', committed_at = now() WHERE id = 'f1000000-0000-4000-8000-000000000001';
UPDATE electriplan.plan_level SET current_floor_plan_version_id = 'f1000000-0000-4000-8000-000000000001';
SELECT pg_temp.must_fail($$UPDATE electriplan.floor_plan_version SET note = 'x' WHERE id = 'f1000000-0000-4000-8000-000000000001'$$, 'changing a committed floor plan');
SELECT pg_temp.must_fail($$DELETE FROM electriplan.floor_plan_version WHERE id = 'f1000000-0000-4000-8000-000000000001'$$, 'deleting a committed floor plan');
SELECT pg_temp.must_fail($$INSERT INTO electriplan.floor_plan_version (organisation_id, plan_level_id, version_no, origin, document)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001', 1, 'editor', '{}')$$, 'reusing a version number');

-- Electrical
INSERT INTO electriplan.electrical_licence (id, user_id, state, licence_class, number) VALUES
  ('a2000000-0000-4000-8000-000000000001', '33333333-3333-4333-8333-333333333333', 'VIC', 'registered_electrical_contractor', 'REC-12345');
INSERT INTO electriplan.rule_pack (id, code, version, content_sha256) VALUES ('a3000000-0000-4000-8000-000000000001', 'au-residential', '2026.1', sha256('pack'));
SELECT pg_temp.must_fail($$UPDATE electriplan.rule_pack SET status = 'released'$$, 'releasing a rule pack nobody signed off');
INSERT INTO electriplan.electrical_brief (plan_id, organisation_id, distributor_code) VALUES ('d0000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'citipower');
INSERT INTO electriplan.electrical_design_version (id, organisation_id, plan_id, version_no, origin, rule_pack_id, engine_version, brief, document, mandatory_violation_count)
  VALUES ('a4000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001', 1, 'engine', 'a3000000-0000-4000-8000-000000000001', '0.1.0', '{}', '{}', 2);
INSERT INTO electriplan.electrical_design_input VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'a4000000-0000-4000-8000-000000000001', 'f1000000-0000-4000-8000-000000000001');
SELECT pg_temp.must_fail($$INSERT INTO electriplan.review (organisation_id, plan_id, design_version_id, requested_by)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001', 'a4000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111')$$, 'reviewing a draft design');
SELECT pg_temp.must_fail($$UPDATE electriplan.electrical_design_version SET state = 'committed', committed_at = now()$$, 'committing a design that breaks mandatory rules');
UPDATE electriplan.electrical_design_version SET mandatory_violation_count = 0;
UPDATE electriplan.electrical_design_version SET state = 'committed', committed_at = now();
UPDATE electriplan.plan SET current_electrical_design_id = 'a4000000-0000-4000-8000-000000000001';
INSERT INTO electriplan.bom_line (organisation_id, design_version_id, item_code, description, category, quantity, unit) VALUES
  ('aaaaaaaa-0000-4000-8000-000000000001', 'a4000000-0000-4000-8000-000000000001', 'DL-IC4-10W', 'IC-4 downlight 10 W', 'luminaire', 12, 'each');
INSERT INTO electriplan.review (id, organisation_id, plan_id, design_version_id, requested_by, reviewer_id)
  VALUES ('a5000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001', 'a4000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', '33333333-3333-4333-8333-333333333333');
SELECT pg_temp.must_fail($$UPDATE electriplan.review SET status = 'approved', decided_at = now()$$, 'approving without a licence');
UPDATE electriplan.review SET status = 'approved', decided_at = now(), reviewer_licence_id = 'a2000000-0000-4000-8000-000000000001', licence_snapshot = '{"number":"REC-12345"}';
SELECT pg_temp.ok(true, 'approval under a licence is accepted');

-- Quote
INSERT INTO electriplan.quote (id, organisation_id, plan_id, design_version_id, client_id, reference)
  VALUES ('a6000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001',
          'a4000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001',
          electriplan.next_reference('aaaaaaaa-0000-4000-8000-000000000001', 'quote', 'Q'));
SELECT pg_temp.must_fail($$INSERT INTO electriplan.quote (organisation_id, plan_id, design_version_id, reference)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001', 'a4000000-0000-4000-8000-000000000001', 'Q-X')$$, 'a second live quote for the same house');
-- A second house in the same project, with a design of its own
INSERT INTO electriplan.plan (id, organisation_id, project_id, name) VALUES
  ('d0000000-0000-4000-8000-000000000002', 'aaaaaaaa-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', 'Lot 13 Type B');
INSERT INTO electriplan.electrical_design_version (id, organisation_id, plan_id, version_no, origin, rule_pack_id, engine_version, brief, document)
  VALUES ('a4000000-0000-4000-8000-000000000002', 'aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000002', 1, 'engine', 'a3000000-0000-4000-8000-000000000001', '0.1.0', '{}', '{}');
SELECT pg_temp.must_fail($$INSERT INTO electriplan.quote (organisation_id, plan_id, design_version_id, reference)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000002', 'a4000000-0000-4000-8000-000000000001', 'Q-Y')$$, 'quoting one house with another house''s design');
SELECT pg_temp.must_fail($$INSERT INTO electriplan.quote (organisation_id, plan_id, design_version_id, reference)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000002', 'a4000000-0000-4000-8000-000000000002', 'Q-Z')$$, 'quoting a draft design');
INSERT INTO electriplan.quote_line (organisation_id, quote_id, line_no, kind, item_code, description, quantity, unit, unit_price_ex_gst, source) VALUES
  ('aaaaaaaa-0000-4000-8000-000000000001', 'a6000000-0000-4000-8000-000000000001', 1, 'material', 'DL-IC4-10W', 'Downlights', 12, 'each', 24.50, 'bom'),
  ('aaaaaaaa-0000-4000-8000-000000000001', 'a6000000-0000-4000-8000-000000000001', 2, 'labour', NULL, 'Installation', 6.5, 'hour', 95.00, 'manual'),
  ('aaaaaaaa-0000-4000-8000-000000000001', 'a6000000-0000-4000-8000-000000000001', 3, 'discount', NULL, 'Package discount', 1, 'lot', -50.00, 'manual');
SELECT pg_temp.ok((SELECT (subtotal_ex_gst, gst_amount, total_inc_gst) = (861.50, 86.15, 947.65) FROM electriplan.quote),
                  'quote totals follow the lines: 294.00 + 617.50 - 50.00 = 861.50, GST 86.15, total 947.65');
SELECT pg_temp.must_fail($$INSERT INTO electriplan.quote_line (organisation_id, quote_id, line_no, kind, description, quantity, unit, unit_price_ex_gst)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'a6000000-0000-4000-8000-000000000001', 4, 'material', 'Negative material', 1, 'each', -5)$$, 'a negative price on a material line');
UPDATE electriplan.quote SET status = 'sent', sent_at = now();
SELECT pg_temp.must_fail($$UPDATE electriplan.quote_line SET quantity = 20 WHERE line_no = 1$$, 'changing a line on a sent quote');
SELECT pg_temp.must_fail($$DELETE FROM electriplan.quote$$, 'deleting a sent quote');
SELECT pg_temp.must_fail($$DELETE FROM electriplan.plan WHERE id = 'd0000000-0000-4000-8000-000000000001'$$, 'deleting a house that has been quoted');
-- Revising: the sent quote is superseded, and a new revision becomes the live one
UPDATE electriplan.quote SET status = 'superseded' WHERE id = 'a6000000-0000-4000-8000-000000000001';
INSERT INTO electriplan.quote (organisation_id, plan_id, design_version_id, reference, revision, supersedes_quote_id)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001', 'a4000000-0000-4000-8000-000000000001',
          'Q-000001', 2, 'a6000000-0000-4000-8000-000000000001');
SELECT pg_temp.ok((SELECT count(*) FROM electriplan.quote WHERE plan_id = 'd0000000-0000-4000-8000-000000000001') = 2, 'a revision supersedes the quote before it, which stays as history');

-- Licences and seats (V3): owners, admins, builders and electricians use a seat; viewers never do
INSERT INTO electriplan.supabase_user (id, email, created_at) VALUES
  ('44444444-4444-4444-8444-444444444444', 'newbuilder@a.com', now()),
  ('55555555-5555-4555-8555-555555555555', 'homeowner@a.com', now());
SELECT pg_temp.ok((SELECT seat_limit = 3 AND licence_ends_on - licence_starts_on = 14 AND status = 'trial'
                     FROM electriplan.organisation WHERE id = 'aaaaaaaa-0000-4000-8000-000000000001'), 'a new company is a trial: 3 seats for 14 days');
SELECT pg_temp.ok(electriplan.seats_in_use('aaaaaaaa-0000-4000-8000-000000000001') = 2, 'the owner and the electrician use 2 seats');
INSERT INTO electriplan.organisation_invitation (id, organisation_id, email, role, token_sha256, invited_by, expires_at)
  VALUES ('a7000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'newbuilder@a.com', 'builder', sha256('t1'), '11111111-1111-4111-8111-111111111111', now() + interval '7 days');
SELECT pg_temp.ok(electriplan.seats_in_use('aaaaaaaa-0000-4000-8000-000000000001') = 3, 'a pending invitation holds a seat');
SELECT pg_temp.must_fail($$INSERT INTO electriplan.organisation_invitation (organisation_id, email, role, token_sha256, invited_by, expires_at)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'fourth@a.com', 'electrician', sha256('t2'), '11111111-1111-4111-8111-111111111111', now() + interval '7 days')$$, 'inviting past the seat limit');
INSERT INTO electriplan.organisation_invitation (organisation_id, email, role, token_sha256, invited_by, expires_at)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'homeowner@a.com', 'viewer', sha256('t3'), '11111111-1111-4111-8111-111111111111', now() + interval '7 days');
INSERT INTO electriplan.organisation_member (organisation_id, user_id, role) VALUES ('aaaaaaaa-0000-4000-8000-000000000001', '55555555-5555-4555-8555-555555555555', 'viewer');
SELECT pg_temp.ok(electriplan.seats_in_use('aaaaaaaa-0000-4000-8000-000000000001') = 3, 'viewers, invited or joined, are free');
SELECT pg_temp.must_fail($$UPDATE electriplan.organisation_member SET role = 'builder' WHERE user_id = '55555555-5555-4555-8555-555555555555'$$,
                         'promoting a viewer when every seat is taken');
INSERT INTO electriplan.organisation_invitation (organisation_id, email, role, token_sha256, invited_by, expires_at, created_at)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'late@a.com', 'builder', sha256('t4'), '11111111-1111-4111-8111-111111111111', now() - interval '1 day', now() - interval '8 days');
SELECT pg_temp.ok(electriplan.seats_in_use('aaaaaaaa-0000-4000-8000-000000000001') = 3, 'an expired invitation holds no seat');
-- Accepting: mark the invitation accepted, then add the member, in one transaction
UPDATE electriplan.organisation_invitation SET accepted_at = now(), accepted_user_id = '44444444-4444-4444-8444-444444444444'
 WHERE id = 'a7000000-0000-4000-8000-000000000001';
INSERT INTO electriplan.organisation_member (organisation_id, user_id, role) VALUES ('aaaaaaaa-0000-4000-8000-000000000001', '44444444-4444-4444-8444-444444444444', 'builder');
SELECT pg_temp.ok(electriplan.seats_in_use('aaaaaaaa-0000-4000-8000-000000000001') = 3, 'accepting an invitation moves its seat to the new member');
UPDATE electriplan.organisation_member SET status = 'suspended' WHERE user_id = '44444444-4444-4444-8444-444444444444';
SELECT pg_temp.ok(electriplan.seats_in_use('aaaaaaaa-0000-4000-8000-000000000001') = 2, 'suspending a member frees their seat');
UPDATE electriplan.organisation_member SET role = 'builder' WHERE user_id = '55555555-5555-4555-8555-555555555555';
SELECT pg_temp.ok(electriplan.seats_in_use('aaaaaaaa-0000-4000-8000-000000000001') = 3, 'a viewer can be promoted into a free seat');
SELECT pg_temp.must_fail($$UPDATE electriplan.organisation_member SET status = 'active' WHERE user_id = '44444444-4444-4444-8444-444444444444'$$,
                         'reactivating a member when every seat is taken');
UPDATE electriplan.organisation SET seat_limit = 1 WHERE id = 'aaaaaaaa-0000-4000-8000-000000000001';
SELECT pg_temp.ok(electriplan.seats_in_use('aaaaaaaa-0000-4000-8000-000000000001') = 3, 'the operator may lower the limit below current use');
SELECT pg_temp.must_fail($$INSERT INTO electriplan.organisation_invitation (organisation_id, email, role, token_sha256, invited_by, expires_at)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'more@a.com', 'admin', sha256('t5'), '11111111-1111-4111-8111-111111111111', now() + interval '7 days')$$, 'a new seat while over a lowered limit');
INSERT INTO electriplan.organisation_invitation (organisation_id, email, role, token_sha256, invited_by, expires_at)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'site@a.com', 'viewer', sha256('t6'), '11111111-1111-4111-8111-111111111111', now() + interval '7 days');
UPDATE electriplan.organisation_member SET role = 'viewer' WHERE user_id = '55555555-5555-4555-8555-555555555555';
SELECT pg_temp.ok(electriplan.seats_in_use('aaaaaaaa-0000-4000-8000-000000000001') = 2, 'freeing seats and adding viewers still work while over the limit');
SELECT pg_temp.must_fail($$UPDATE electriplan.organisation SET licence_ends_on = licence_starts_on - 1 WHERE id = 'aaaaaaaa-0000-4000-8000-000000000001'$$, 'a licence that ends before it starts');
SELECT pg_temp.must_fail($$UPDATE electriplan.organisation SET status = 'closed' WHERE id = 'aaaaaaaa-0000-4000-8000-000000000001'$$, 'closing a licence without recording when');
UPDATE electriplan.organisation SET seat_limit = 5, status = 'active', licence_ends_on = NULL WHERE id = 'aaaaaaaa-0000-4000-8000-000000000001';
SELECT pg_temp.ok((SELECT licence_ends_on IS NULL FROM electriplan.organisation WHERE id = 'aaaaaaaa-0000-4000-8000-000000000001'), 'an active licence may have no end date');

-- Membership rules (V6), acting as people in company A: alice (u1) owner, u3 electrician, u5 viewer
SELECT set_config('electriplan.actor_id', '11111111-1111-4111-8111-111111111111', false);
SELECT pg_temp.must_fail($$UPDATE electriplan.organisation_member SET role = 'admin' WHERE user_id = '11111111-1111-4111-8111-111111111111'$$, 'changing your own role');
SELECT pg_temp.must_fail($$UPDATE electriplan.organisation_member SET status = 'suspended' WHERE user_id = '11111111-1111-4111-8111-111111111111'$$, 'suspending yourself');
UPDATE electriplan.organisation_member SET role = 'admin' WHERE user_id = '33333333-3333-4333-8333-333333333333';
SELECT pg_temp.ok(true, 'an owner can change someone else''s role');
-- now as u3, an admin
SELECT set_config('electriplan.actor_id', '33333333-3333-4333-8333-333333333333', false);
SELECT pg_temp.must_fail($$UPDATE electriplan.organisation_member SET role = 'builder' WHERE user_id = '11111111-1111-4111-8111-111111111111'$$, 'an admin demoting an owner');
SELECT pg_temp.must_fail($$UPDATE electriplan.organisation_member SET status = 'suspended' WHERE user_id = '11111111-1111-4111-8111-111111111111'$$, 'an admin suspending an owner');
SELECT pg_temp.must_fail($$DELETE FROM electriplan.organisation_member WHERE user_id = '11111111-1111-4111-8111-111111111111'$$, 'an admin removing an owner');
SELECT pg_temp.must_fail($$UPDATE electriplan.organisation_member SET role = 'owner' WHERE user_id = '55555555-5555-4555-8555-555555555555'$$, 'an admin making someone an owner');
UPDATE electriplan.organisation_member SET status = 'suspended' WHERE user_id = '55555555-5555-4555-8555-555555555555';
UPDATE electriplan.organisation_member SET status = 'active' WHERE user_id = '55555555-5555-4555-8555-555555555555';
SELECT pg_temp.ok(true, 'an admin manages members who are not owners');
-- back as alice: a second owner, then the last-owner rule
SELECT set_config('electriplan.actor_id', '11111111-1111-4111-8111-111111111111', false);
UPDATE electriplan.organisation_member SET role = 'owner' WHERE user_id = '33333333-3333-4333-8333-333333333333';
SELECT pg_temp.ok((SELECT count(*) FROM electriplan.organisation_member WHERE organisation_id = 'aaaaaaaa-0000-4000-8000-000000000001' AND role = 'owner') = 2, 'an owner can make someone an owner');
SELECT set_config('electriplan.actor_id', '33333333-3333-4333-8333-333333333333', false);
UPDATE electriplan.organisation_member SET role = 'admin' WHERE user_id = '11111111-1111-4111-8111-111111111111';
SELECT pg_temp.ok(true, 'one owner can demote another while an owner remains');
SELECT pg_temp.must_fail($$DELETE FROM electriplan.organisation_member WHERE user_id = '33333333-3333-4333-8333-333333333333'$$, 'the last owner leaving');
SELECT set_config('electriplan.actor_id', '', false);
SELECT pg_temp.must_fail($$UPDATE electriplan.organisation_member SET role = 'admin' WHERE user_id = '33333333-3333-4333-8333-333333333333'$$, 'even the operator demoting the last owner');
SELECT pg_temp.must_fail($$UPDATE electriplan.organisation_member SET status = 'suspended' WHERE user_id = '33333333-3333-4333-8333-333333333333'$$, 'suspending the last owner');
UPDATE electriplan.organisation_member SET role = 'owner' WHERE user_id = '11111111-1111-4111-8111-111111111111';
SELECT pg_temp.ok(true, 'the operator (no actor) may manage owners');
-- deleting a whole company takes its memberships with it
SELECT set_config('electriplan.organisation_id', 'eeeeeeee-0000-4000-8000-00000000000e', false),
       set_config('electriplan.actor_id', '11111111-1111-4111-8111-111111111111', false);
INSERT INTO electriplan.organisation (id, name, slug) VALUES ('eeeeeeee-0000-4000-8000-00000000000e', 'Short Lived', 'short-lived');
INSERT INTO electriplan.organisation_member (organisation_id, user_id, role) VALUES ('eeeeeeee-0000-4000-8000-00000000000e', '11111111-1111-4111-8111-111111111111', 'owner');
SELECT pg_temp.ok(true, 'a new company''s first owner can be added by anyone');
DELETE FROM electriplan.organisation WHERE id = 'eeeeeeee-0000-4000-8000-00000000000e';
SELECT pg_temp.ok(true, 'deleting a company deletes its last owner''s membership with it');
SELECT set_config('electriplan.organisation_id', 'aaaaaaaa-0000-4000-8000-000000000001', false), set_config('electriplan.actor_id', '11111111-1111-4111-8111-111111111111', false);

-- Organisation B
SELECT set_config('electriplan.organisation_id', 'bbbbbbbb-0000-4000-8000-000000000002', false),
       set_config('electriplan.actor_id', '22222222-2222-4222-8222-222222222222', false);
INSERT INTO electriplan.organisation (id, name, slug) VALUES ('bbbbbbbb-0000-4000-8000-000000000002', 'Bolt Electrical', 'bolt-electrical');
INSERT INTO electriplan.organisation_member VALUES ('bbbbbbbb-0000-4000-8000-000000000002', '22222222-2222-4222-8222-222222222222', 'owner');
SELECT pg_temp.ok(electriplan.seats_in_use('bbbbbbbb-0000-4000-8000-000000000002') = 1, 'each company counts only its own seats');
SELECT pg_temp.ok((SELECT count(*) FROM electriplan.project) = 0, 'organisation B sees none of A''s projects');
SELECT pg_temp.ok((SELECT count(*) FROM electriplan.quote) = 0, 'organisation B sees none of A''s quotes');
SELECT pg_temp.ok((SELECT count(*) FROM electriplan.organisation) = 1, 'organisation B sees only itself');
SELECT pg_temp.must_fail($$INSERT INTO electriplan.project (organisation_id, reference, name) VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'X', 'sneaky')$$, 'B writing into A');
WITH u AS (UPDATE electriplan.plan SET name = 'hijacked' RETURNING 1) SELECT pg_temp.ok((SELECT count(*) FROM u) = 0, 'B updating A''s plan touches nothing');
INSERT INTO electriplan.project (id, organisation_id, reference, name) VALUES ('b0000000-0000-4000-8000-000000000002', 'bbbbbbbb-0000-4000-8000-000000000002', 'PRJ-000001', 'B site');
SELECT pg_temp.must_fail($$INSERT INTO electriplan.plan (organisation_id, project_id, name) VALUES ('bbbbbbbb-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000001', 'cross')$$, 'B pointing a plan at A''s project');

-- The builder from A, while working in B: A is invisible, even to its own member (V5)
SELECT set_config('electriplan.actor_id', '11111111-1111-4111-8111-111111111111', false);
SELECT pg_temp.ok((SELECT count(*) FROM electriplan.organisation WHERE id = 'aaaaaaaa-0000-4000-8000-000000000001') = 0, 'inside a company, the member''s other companies are invisible');
SELECT pg_temp.ok((SELECT count(*) FROM electriplan.organisation_member WHERE user_id = '11111111-1111-4111-8111-111111111111') = 0, 'and so are their memberships there');
-- With no company chosen (the company switcher), they list their own companies and memberships
SELECT set_config('electriplan.organisation_id', '', false);
SELECT pg_temp.ok((SELECT count(*) FROM electriplan.organisation WHERE id = 'aaaaaaaa-0000-4000-8000-000000000001') = 1, 'with no company chosen, a member sees the companies they belong to');
SELECT pg_temp.ok((SELECT count(*) FROM electriplan.organisation_member WHERE user_id = '11111111-1111-4111-8111-111111111111') = 1, 'and their own memberships');
SELECT pg_temp.ok((SELECT count(*) FROM electriplan.organisation WHERE id = 'bbbbbbbb-0000-4000-8000-000000000002') = 0, 'but not companies they do not belong to');

-- No organisation set: nothing at all
SELECT set_config('electriplan.organisation_id', '', false), set_config('electriplan.actor_id', '', false);
SELECT pg_temp.ok((SELECT count(*) FROM electriplan.project) + (SELECT count(*) FROM electriplan.plan) + (SELECT count(*) FROM electriplan.organisation) = 0,
                  'without an organisation every tenant table is empty (fails closed)');

-- Platform catalogue
SELECT pg_temp.must_fail($$INSERT INTO electriplan.catalogue_item (item_code, name, category, unit) VALUES ('GPO-DBL', 'Double GPO', 'outlet', 'each')$$, 'an organisation writing the platform catalogue');
SELECT set_config('electriplan.platform_admin', 'on', false);
INSERT INTO electriplan.catalogue_item (item_code, name, category, unit) VALUES ('GPO-DBL', 'Double GPO', 'outlet', 'each');
SELECT set_config('electriplan.platform_admin', '', false), set_config('electriplan.organisation_id', 'bbbbbbbb-0000-4000-8000-000000000002', false);
SELECT pg_temp.ok((SELECT count(*) FROM electriplan.catalogue_item) = 1, 'every organisation reads the platform catalogue');
SELECT pg_temp.must_fail($$INSERT INTO electriplan.catalogue_item (item_code, name, category, unit) VALUES ('GPO-DBL', 'Dup', 'outlet', 'each')$$, 'an organisation adding to the platform catalogue');
