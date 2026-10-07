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

INSERT INTO app.supabase_user (id, email, created_at) VALUES
  ('11111111-1111-4111-8111-111111111111', 'builder@a.com', now()),
  ('22222222-2222-4222-8222-222222222222', 'other@b.com', now()),
  ('33333333-3333-4333-8333-333333333333', 'sparky@a.com', now());

-- Organisation A, acting as its owner
SELECT set_config('app.organisation_id', 'aaaaaaaa-0000-4000-8000-000000000001', false),
       set_config('app.actor_id', '11111111-1111-4111-8111-111111111111', false);
INSERT INTO app.organisation (id, name, slug) VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'Acme Homes', 'acme-homes');
INSERT INTO app.organisation_member VALUES ('aaaaaaaa-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'owner');
INSERT INTO app.organisation_member (organisation_id, user_id, role) VALUES ('aaaaaaaa-0000-4000-8000-000000000001', '33333333-3333-4333-8333-333333333333', 'electrician');
INSERT INTO app.client (id, organisation_id, name) VALUES ('c0000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'Sam Lee');
INSERT INTO app.project (id, organisation_id, reference, name, client_id, distributor_code)
  VALUES ('b0000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001',
          app.next_reference('aaaaaaaa-0000-4000-8000-000000000001', 'project', 'PRJ'), '12 Example St', 'c0000000-0000-4000-8000-000000000001', 'citipower');
SELECT pg_temp.ok((SELECT reference FROM app.project) = 'PRJ-000001', 'first project reference is PRJ-000001');
SELECT pg_temp.ok(app.next_reference('aaaaaaaa-0000-4000-8000-000000000001', 'project', 'PRJ') = 'PRJ-000002', 'references count up');

INSERT INTO app.plan (id, organisation_id, project_id, name) VALUES
  ('d0000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', 'Lot 12 Type A');
SELECT pg_temp.ok((SELECT count(*) FROM app.plan_stage_event WHERE to_stage = 'awaiting_upload' AND from_stage IS NULL
                   AND actor_id = '11111111-1111-4111-8111-111111111111') = 1, 'creating a plan records its first stage and who');
SELECT pg_temp.must_fail($$UPDATE app.plan SET stage = 'won'$$, 'jumping from awaiting_upload to won');
SELECT set_config('app.stage_note', 'uploaded plan.png', false);
UPDATE app.plan SET stage = 'analysing';
SELECT pg_temp.ok((SELECT note FROM app.plan_stage_event WHERE to_stage = 'analysing') = 'uploaded plan.png', 'a valid move is recorded with its note');
UPDATE app.plan SET stage = 'on_hold';
UPDATE app.plan SET stage = 'awaiting_upload';
SELECT pg_temp.ok((SELECT count(*) FROM app.plan_stage_event) = 4, 'pause and resume are recorded');

INSERT INTO app.plan_level (id, organisation_id, plan_id, name, ordinal) VALUES
  ('e0000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001', 'Ground floor', 0);
INSERT INTO app.stored_file (id, organisation_id, purpose, storage_backend, storage_key, content_type, byte_size, sha256)
  VALUES ('f0000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'floor_plan_source', 'local', 'a/plan.png', 'image/png', 1234, sha256('x'));
INSERT INTO app.analysis_run (id, organisation_id, plan_level_id, source_file_id, status, started_at, finished_at)
  VALUES ('a1000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001', 'f0000000-0000-4000-8000-000000000001', 'succeeded', now(), now());
SELECT pg_temp.must_fail($$INSERT INTO app.analysis_run (organisation_id, plan_level_id, source_file_id, status)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001', 'f0000000-0000-4000-8000-000000000001', 'failed')$$, 'a failed run without an error message');
INSERT INTO app.floor_plan_version (id, organisation_id, plan_level_id, version_no, origin, analysis_run_id, document, room_count, floor_area_m2)
  VALUES ('f1000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001', 1, 'analysis', 'a1000000-0000-4000-8000-000000000001', '{"version":1}', 3, 19.86);
SELECT pg_temp.must_fail($$INSERT INTO app.floor_plan_version (organisation_id, plan_level_id, version_no, origin, document)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001', 2, 'editor', '{}')$$, 'a second draft on the same level');
UPDATE app.floor_plan_version SET document = '{"version":1,"walls":[]}' WHERE id = 'f1000000-0000-4000-8000-000000000001';
UPDATE app.floor_plan_version SET state = 'committed', committed_at = now() WHERE id = 'f1000000-0000-4000-8000-000000000001';
UPDATE app.plan_level SET current_floor_plan_version_id = 'f1000000-0000-4000-8000-000000000001';
SELECT pg_temp.must_fail($$UPDATE app.floor_plan_version SET note = 'x' WHERE id = 'f1000000-0000-4000-8000-000000000001'$$, 'changing a committed floor plan');
SELECT pg_temp.must_fail($$DELETE FROM app.floor_plan_version WHERE id = 'f1000000-0000-4000-8000-000000000001'$$, 'deleting a committed floor plan');
SELECT pg_temp.must_fail($$INSERT INTO app.floor_plan_version (organisation_id, plan_level_id, version_no, origin, document)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'e0000000-0000-4000-8000-000000000001', 1, 'editor', '{}')$$, 'reusing a version number');

-- Electrical
INSERT INTO app.electrical_licence (id, user_id, state, licence_class, number) VALUES
  ('a2000000-0000-4000-8000-000000000001', '33333333-3333-4333-8333-333333333333', 'VIC', 'registered_electrical_contractor', 'REC-12345');
INSERT INTO app.rule_pack (id, code, version, content_sha256) VALUES ('a3000000-0000-4000-8000-000000000001', 'au-residential', '2026.1', sha256('pack'));
SELECT pg_temp.must_fail($$UPDATE app.rule_pack SET status = 'released'$$, 'releasing a rule pack nobody signed off');
INSERT INTO app.electrical_brief (plan_id, organisation_id, distributor_code) VALUES ('d0000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'citipower');
INSERT INTO app.electrical_design_version (id, organisation_id, plan_id, version_no, origin, rule_pack_id, engine_version, brief, document, mandatory_violation_count)
  VALUES ('a4000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001', 1, 'engine', 'a3000000-0000-4000-8000-000000000001', '0.1.0', '{}', '{}', 2);
INSERT INTO app.electrical_design_input VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'a4000000-0000-4000-8000-000000000001', 'f1000000-0000-4000-8000-000000000001');
SELECT pg_temp.must_fail($$INSERT INTO app.review (organisation_id, plan_id, design_version_id, requested_by)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001', 'a4000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111')$$, 'reviewing a draft design');
SELECT pg_temp.must_fail($$UPDATE app.electrical_design_version SET state = 'committed', committed_at = now()$$, 'committing a design that breaks mandatory rules');
UPDATE app.electrical_design_version SET mandatory_violation_count = 0;
UPDATE app.electrical_design_version SET state = 'committed', committed_at = now();
UPDATE app.plan SET current_electrical_design_id = 'a4000000-0000-4000-8000-000000000001';
INSERT INTO app.bom_line (organisation_id, design_version_id, item_code, description, category, quantity, unit) VALUES
  ('aaaaaaaa-0000-4000-8000-000000000001', 'a4000000-0000-4000-8000-000000000001', 'DL-IC4-10W', 'IC-4 downlight 10 W', 'luminaire', 12, 'each');
INSERT INTO app.review (id, organisation_id, plan_id, design_version_id, requested_by, reviewer_id)
  VALUES ('a5000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001', 'a4000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', '33333333-3333-4333-8333-333333333333');
SELECT pg_temp.must_fail($$UPDATE app.review SET status = 'approved', decided_at = now()$$, 'approving without a licence');
UPDATE app.review SET status = 'approved', decided_at = now(), reviewer_licence_id = 'a2000000-0000-4000-8000-000000000001', licence_snapshot = '{"number":"REC-12345"}';
SELECT pg_temp.ok(true, 'approval under a licence is accepted');

-- Quote
INSERT INTO app.quote (id, organisation_id, plan_id, design_version_id, client_id, reference)
  VALUES ('a6000000-0000-4000-8000-000000000001', 'aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001',
          'a4000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001',
          app.next_reference('aaaaaaaa-0000-4000-8000-000000000001', 'quote', 'Q'));
SELECT pg_temp.must_fail($$INSERT INTO app.quote (organisation_id, plan_id, design_version_id, reference)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001', 'a4000000-0000-4000-8000-000000000001', 'Q-X')$$, 'a second live quote for the same house');
-- A second house in the same project, with a design of its own
INSERT INTO app.plan (id, organisation_id, project_id, name) VALUES
  ('d0000000-0000-4000-8000-000000000002', 'aaaaaaaa-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', 'Lot 13 Type B');
INSERT INTO app.electrical_design_version (id, organisation_id, plan_id, version_no, origin, rule_pack_id, engine_version, brief, document)
  VALUES ('a4000000-0000-4000-8000-000000000002', 'aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000002', 1, 'engine', 'a3000000-0000-4000-8000-000000000001', '0.1.0', '{}', '{}');
SELECT pg_temp.must_fail($$INSERT INTO app.quote (organisation_id, plan_id, design_version_id, reference)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000002', 'a4000000-0000-4000-8000-000000000001', 'Q-Y')$$, 'quoting one house with another house''s design');
SELECT pg_temp.must_fail($$INSERT INTO app.quote (organisation_id, plan_id, design_version_id, reference)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000002', 'a4000000-0000-4000-8000-000000000002', 'Q-Z')$$, 'quoting a draft design');
INSERT INTO app.quote_line (organisation_id, quote_id, line_no, kind, item_code, description, quantity, unit, unit_price_ex_gst, source) VALUES
  ('aaaaaaaa-0000-4000-8000-000000000001', 'a6000000-0000-4000-8000-000000000001', 1, 'material', 'DL-IC4-10W', 'Downlights', 12, 'each', 24.50, 'bom'),
  ('aaaaaaaa-0000-4000-8000-000000000001', 'a6000000-0000-4000-8000-000000000001', 2, 'labour', NULL, 'Installation', 6.5, 'hour', 95.00, 'manual'),
  ('aaaaaaaa-0000-4000-8000-000000000001', 'a6000000-0000-4000-8000-000000000001', 3, 'discount', NULL, 'Package discount', 1, 'lot', -50.00, 'manual');
SELECT pg_temp.ok((SELECT (subtotal_ex_gst, gst_amount, total_inc_gst) = (861.50, 86.15, 947.65) FROM app.quote),
                  'quote totals follow the lines: 294.00 + 617.50 - 50.00 = 861.50, GST 86.15, total 947.65');
SELECT pg_temp.must_fail($$INSERT INTO app.quote_line (organisation_id, quote_id, line_no, kind, description, quantity, unit, unit_price_ex_gst)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'a6000000-0000-4000-8000-000000000001', 4, 'material', 'Negative material', 1, 'each', -5)$$, 'a negative price on a material line');
UPDATE app.quote SET status = 'sent', sent_at = now();
SELECT pg_temp.must_fail($$UPDATE app.quote_line SET quantity = 20 WHERE line_no = 1$$, 'changing a line on a sent quote');
SELECT pg_temp.must_fail($$DELETE FROM app.quote$$, 'deleting a sent quote');
SELECT pg_temp.must_fail($$DELETE FROM app.plan WHERE id = 'd0000000-0000-4000-8000-000000000001'$$, 'deleting a house that has been quoted');
-- Revising: the sent quote is superseded, and a new revision becomes the live one
UPDATE app.quote SET status = 'superseded' WHERE id = 'a6000000-0000-4000-8000-000000000001';
INSERT INTO app.quote (organisation_id, plan_id, design_version_id, reference, revision, supersedes_quote_id)
  VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001', 'a4000000-0000-4000-8000-000000000001',
          'Q-000001', 2, 'a6000000-0000-4000-8000-000000000001');
SELECT pg_temp.ok((SELECT count(*) FROM app.quote WHERE plan_id = 'd0000000-0000-4000-8000-000000000001') = 2, 'a revision supersedes the quote before it, which stays as history');

-- Organisation B
SELECT set_config('app.organisation_id', 'bbbbbbbb-0000-4000-8000-000000000002', false),
       set_config('app.actor_id', '22222222-2222-4222-8222-222222222222', false);
INSERT INTO app.organisation (id, name, slug) VALUES ('bbbbbbbb-0000-4000-8000-000000000002', 'Bolt Electrical', 'bolt-electrical');
INSERT INTO app.organisation_member VALUES ('bbbbbbbb-0000-4000-8000-000000000002', '22222222-2222-4222-8222-222222222222', 'owner');
SELECT pg_temp.ok((SELECT count(*) FROM app.project) = 0, 'organisation B sees none of A''s projects');
SELECT pg_temp.ok((SELECT count(*) FROM app.quote) = 0, 'organisation B sees none of A''s quotes');
SELECT pg_temp.ok((SELECT count(*) FROM app.organisation) = 1, 'organisation B sees only itself');
SELECT pg_temp.must_fail($$INSERT INTO app.project (organisation_id, reference, name) VALUES ('aaaaaaaa-0000-4000-8000-000000000001', 'X', 'sneaky')$$, 'B writing into A');
WITH u AS (UPDATE app.plan SET name = 'hijacked' RETURNING 1) SELECT pg_temp.ok((SELECT count(*) FROM u) = 0, 'B updating A''s plan touches nothing');
INSERT INTO app.project (id, organisation_id, reference, name) VALUES ('b0000000-0000-4000-8000-000000000002', 'bbbbbbbb-0000-4000-8000-000000000002', 'PRJ-000001', 'B site');
SELECT pg_temp.must_fail($$INSERT INTO app.plan (organisation_id, project_id, name) VALUES ('bbbbbbbb-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000001', 'cross')$$, 'B pointing a plan at A''s project');

-- The builder from A, while acting in B's context, still lists their own organisations
SELECT set_config('app.actor_id', '11111111-1111-4111-8111-111111111111', false);
SELECT pg_temp.ok((SELECT count(*) FROM app.organisation WHERE id = 'aaaaaaaa-0000-4000-8000-000000000001') = 1, 'a member can always see the organisations they belong to');
SELECT pg_temp.ok((SELECT count(*) FROM app.organisation_member WHERE user_id = '11111111-1111-4111-8111-111111111111') = 1, 'and their own memberships');

-- No organisation set: nothing at all
SELECT set_config('app.organisation_id', '', false), set_config('app.actor_id', '', false);
SELECT pg_temp.ok((SELECT count(*) FROM app.project) + (SELECT count(*) FROM app.plan) + (SELECT count(*) FROM app.organisation) = 0,
                  'without an organisation every tenant table is empty (fails closed)');

-- Platform catalogue
SELECT pg_temp.must_fail($$INSERT INTO app.catalogue_item (item_code, name, category, unit) VALUES ('GPO-DBL', 'Double GPO', 'outlet', 'each')$$, 'an organisation writing the platform catalogue');
SELECT set_config('app.platform_admin', 'on', false);
INSERT INTO app.catalogue_item (item_code, name, category, unit) VALUES ('GPO-DBL', 'Double GPO', 'outlet', 'each');
SELECT set_config('app.platform_admin', '', false), set_config('app.organisation_id', 'bbbbbbbb-0000-4000-8000-000000000002', false);
SELECT pg_temp.ok((SELECT count(*) FROM app.catalogue_item) = 1, 'every organisation reads the platform catalogue');
SELECT pg_temp.must_fail($$INSERT INTO app.catalogue_item (item_code, name, category, unit) VALUES ('GPO-DBL', 'Dup', 'outlet', 'each')$$, 'an organisation adding to the platform catalogue');
