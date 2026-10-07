-- =============================================================================
-- V2: the core domain. Organisations (tenants) and their people, projects,
-- house plans and their lifecycle, uploaded files, floor-plan analysis and
-- versions, electrical briefs and design versions, electrician review, the
-- catalogue, quotes, external (ERP) references and an audit trail.
--
-- Design notes are in documents/database-schema.md. In short:
--   * Every tenant-owned row carries organisation_id, and row-level security
--     limits every query to plannasaas.current_organisation_id(). The API must
--     SET LOCAL plannasaas.organisation_id (and plannasaas.actor_id) in each transaction;
--     without it these tables return nothing (fail closed).
--   * References between tenant rows go through (organisation_id, id), so a
--     row can never point at another organisation's row.
--   * Floor plans and electrical designs are JSON documents, versioned: one
--     editable draft, then immutable committed versions.
--   * A plan's stage can only move along plannasaas.plan_stage_transition; every
--     move is recorded in plannasaas.plan_stage_event by trigger.
--   * Codes are text with CHECK constraints rather than enum types, so values
--     can be added and retired by migration without ALTER TYPE.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Shared functions
-- -----------------------------------------------------------------------------

CREATE FUNCTION plannasaas.current_organisation_id() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT nullif(current_setting('plannasaas.organisation_id', true), '')::uuid $$;

COMMENT ON FUNCTION plannasaas.current_organisation_id() IS
    'The organisation this transaction acts for, from SET LOCAL plannasaas.organisation_id. NULL when unset, which row-level security treats as no access.';

CREATE FUNCTION plannasaas.current_actor_id() RETURNS uuid
    LANGUAGE sql STABLE
AS $$ SELECT nullif(current_setting('plannasaas.actor_id', true), '')::uuid $$;

COMMENT ON FUNCTION plannasaas.current_actor_id() IS
    'The Supabase user acting in this transaction, from SET LOCAL plannasaas.actor_id. Recorded by triggers in history rows.';

CREATE FUNCTION plannasaas.touch_updated_at() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END
$$;

-- -----------------------------------------------------------------------------
-- Reference data (shared by all tenants, no row-level security)
-- -----------------------------------------------------------------------------

CREATE TABLE plannasaas.electricity_distributor (
    code  text PRIMARY KEY CHECK (code ~ '^[a-z0-9_]+$'),
    name  text NOT NULL,
    state text NOT NULL CHECK (state IN ('NSW', 'VIC', 'QLD', 'WA', 'SA', 'TAS', 'ACT', 'NT'))
);

COMMENT ON TABLE plannasaas.electricity_distributor IS
    'Distribution network service providers (DNSPs). Their service rules decide supply limits and consumer mains.';

INSERT INTO plannasaas.electricity_distributor (code, name, state) VALUES
    ('citipower',       'CitiPower',        'VIC'),
    ('powercor',        'Powercor',         'VIC'),
    ('jemena',          'Jemena',           'VIC'),
    ('united_energy',   'United Energy',    'VIC'),
    ('ausnet_services', 'AusNet Services',  'VIC');

CREATE TABLE plannasaas.plan_stage (
    code        text PRIMARY KEY,
    ordinal     smallint NOT NULL UNIQUE,
    label       text NOT NULL,
    phase       text NOT NULL CHECK (phase IN ('floor_plan', 'electrical', 'review', 'quote', 'closed', 'paused')),
    is_terminal boolean NOT NULL DEFAULT false
);

COMMENT ON TABLE plannasaas.plan_stage IS 'The stages a house plan moves through, in display order.';

INSERT INTO plannasaas.plan_stage (code, ordinal, label, phase, is_terminal) VALUES
    ('awaiting_upload',      10, 'Awaiting floor plan',      'floor_plan', false),
    ('analysing',            20, 'Analysing floor plan',     'floor_plan', false),
    ('floor_plan_review',    30, 'Checking floor plan',      'floor_plan', false),
    ('floor_plan_approved',  40, 'Floor plan approved',      'floor_plan', false),
    ('electrical_design',    50, 'Designing electrical',     'electrical', false),
    ('electrical_review',    60, 'With electrician',         'review',     false),
    ('changes_requested',    70, 'Changes requested',        'review',     false),
    ('design_approved',      80, 'Design approved',          'review',     false),
    ('quoting',              90, 'Preparing quote',          'quote',      false),
    ('quote_sent',          100, 'Quote sent',               'quote',      false),
    ('won',                 110, 'Won',                      'closed',     true),
    ('lost',                120, 'Lost',                     'closed',     true),
    ('on_hold',             130, 'On hold',                  'paused',     false),
    ('archived',            140, 'Archived',                 'closed',     true);

CREATE TABLE plannasaas.plan_stage_transition (
    from_stage text NOT NULL REFERENCES plannasaas.plan_stage (code),
    to_stage   text NOT NULL REFERENCES plannasaas.plan_stage (code),
    PRIMARY KEY (from_stage, to_stage),
    CHECK (from_stage <> to_stage)
);

COMMENT ON TABLE plannasaas.plan_stage_transition IS
    'Every move a plan may make. A trigger on plannasaas.plan refuses any other. Change the workflow by changing these rows.';

INSERT INTO plannasaas.plan_stage_transition (from_stage, to_stage) VALUES
    -- floor plan
    ('awaiting_upload',     'analysing'),
    ('awaiting_upload',     'floor_plan_review'),   -- traced by hand or imported, no analysis
    ('analysing',           'floor_plan_review'),
    ('analysing',           'awaiting_upload'),     -- analysis failed
    ('floor_plan_review',   'analysing'),           -- re-analysed from a new image
    ('floor_plan_review',   'floor_plan_approved'),
    ('floor_plan_approved', 'floor_plan_review'),   -- reopened
    -- electrical
    ('floor_plan_approved', 'electrical_design'),
    ('electrical_design',   'floor_plan_review'),   -- the floor plan needs fixing first
    ('electrical_design',   'electrical_review'),
    -- review
    ('electrical_review',   'changes_requested'),
    ('electrical_review',   'design_approved'),
    ('electrical_review',   'electrical_design'),   -- review withdrawn
    ('changes_requested',   'electrical_design'),
    ('design_approved',     'electrical_design'),   -- reopened after approval: needs review again
    -- quote
    ('design_approved',     'quoting'),
    ('quoting',             'quote_sent'),
    ('quoting',             'design_approved'),
    ('quote_sent',          'quoting'),             -- revised quote
    ('quote_sent',          'won'),
    ('quote_sent',          'lost'),
    ('lost',                'quoting');             -- re-quoted

-- Any working stage can be paused or archived; a paused plan resumes where it
-- makes sense. Generated rather than listed so no stage is forgotten.
INSERT INTO plannasaas.plan_stage_transition (from_stage, to_stage)
SELECT code, 'on_hold' FROM plannasaas.plan_stage WHERE NOT is_terminal AND code <> 'on_hold'
UNION ALL
SELECT code, 'archived' FROM plannasaas.plan_stage WHERE code <> 'archived'
UNION ALL
SELECT 'on_hold', code FROM plannasaas.plan_stage
 WHERE code IN ('awaiting_upload', 'floor_plan_review', 'floor_plan_approved', 'electrical_design', 'quoting');

-- -----------------------------------------------------------------------------
-- Organisations and people
-- -----------------------------------------------------------------------------

CREATE TABLE plannasaas.organisation (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name              text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    slug              text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND length(slug) <= 60),
    kind              text NOT NULL DEFAULT 'builder'
                      CHECK (kind IN ('builder', 'electrical_contractor', 'designer', 'other')),
    abn               text CHECK (abn ~ '^[0-9]{11}$'),
    home_state        text NOT NULL DEFAULT 'VIC'
                      CHECK (home_state IN ('NSW', 'VIC', 'QLD', 'WA', 'SA', 'TAS', 'ACT', 'NT')),
    status            text NOT NULL DEFAULT 'trial' CHECK (status IN ('trial', 'active', 'suspended', 'closed')),
    subscription_plan text NOT NULL DEFAULT 'trial',
    settings          jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(settings) = 'object'),
    created_by        uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE plannasaas.organisation IS 'A customer of the SaaS: the tenant. Everything a business creates belongs to one.';
COMMENT ON COLUMN plannasaas.organisation.abn IS 'Australian Business Number, 11 digits, no spaces.';

CREATE TABLE plannasaas.organisation_member (
    organisation_id uuid NOT NULL REFERENCES plannasaas.organisation (id) ON DELETE CASCADE,
    user_id         uuid NOT NULL REFERENCES plannasaas.supabase_user (id) ON DELETE CASCADE,
    role            text NOT NULL CHECK (role IN ('owner', 'admin', 'builder', 'electrician', 'viewer')),
    status          text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended')),
    invited_by      uuid,
    joined_at       timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (organisation_id, user_id)
);

CREATE INDEX ix_organisation_member_user ON plannasaas.organisation_member (user_id);

COMMENT ON TABLE plannasaas.organisation_member IS
    'Who belongs to which organisation, and as what. The role decides what the API lets them do.';

CREATE TABLE plannasaas.organisation_invitation (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id  uuid NOT NULL REFERENCES plannasaas.organisation (id) ON DELETE CASCADE,
    email            text NOT NULL CHECK (position('@' IN email) > 1),
    role             text NOT NULL CHECK (role IN ('admin', 'builder', 'electrician', 'viewer')),
    token_sha256     bytea NOT NULL UNIQUE CHECK (length(token_sha256) = 32),
    invited_by       uuid NOT NULL,
    expires_at       timestamptz NOT NULL,
    accepted_at      timestamptz,
    accepted_user_id uuid REFERENCES plannasaas.supabase_user (id) ON DELETE SET NULL,
    revoked_at       timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CHECK (accepted_at IS NULL OR revoked_at IS NULL)
);

CREATE UNIQUE INDEX ux_invitation_pending_email ON plannasaas.organisation_invitation (organisation_id, lower(email))
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

COMMENT ON COLUMN plannasaas.organisation_invitation.token_sha256 IS
    'SHA-256 of the invitation token. The token itself is only ever in the email.';

CREATE TABLE plannasaas.user_profile (
    user_id                 uuid PRIMARY KEY REFERENCES plannasaas.supabase_user (id) ON DELETE CASCADE,
    display_name            text CHECK (length(btrim(display_name)) BETWEEN 1 AND 120),
    phone                   text,
    default_organisation_id uuid REFERENCES plannasaas.organisation (id) ON DELETE SET NULL,
    preferences             jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(preferences) = 'object'),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE plannasaas.user_profile IS
    'What this app keeps about a person beyond Supabase: display name, the organisation they land in, preferences.';

CREATE TABLE plannasaas.electrical_licence (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid NOT NULL REFERENCES plannasaas.supabase_user (id) ON DELETE CASCADE,
    state         text NOT NULL CHECK (state IN ('NSW', 'VIC', 'QLD', 'WA', 'SA', 'TAS', 'ACT', 'NT')),
    licence_class text NOT NULL
                  CHECK (licence_class IN ('registered_electrical_contractor', 'licensed_electrician', 'electrical_inspector')),
    number        text NOT NULL CHECK (length(btrim(number)) BETWEEN 1 AND 40),
    expires_on    date,
    verified_at   timestamptz,
    verified_by   uuid,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (state, licence_class, number)
);

COMMENT ON TABLE plannasaas.electrical_licence IS
    'A person''s electrical licence. A review is signed off under one, recorded on the review as it stood then.';

-- Human-readable references (PRJ-000042, Q-000107), gapless per organisation.
CREATE TABLE plannasaas.organisation_counter (
    organisation_id uuid NOT NULL REFERENCES plannasaas.organisation (id) ON DELETE CASCADE,
    counter         text NOT NULL CHECK (counter IN ('project', 'quote')),
    next_value      bigint NOT NULL DEFAULT 1 CHECK (next_value > 0),
    PRIMARY KEY (organisation_id, counter)
);

CREATE FUNCTION plannasaas.next_reference(p_organisation uuid, p_counter text, p_prefix text) RETURNS text
    LANGUAGE plpgsql
AS $$
DECLARE
    v_value bigint;
BEGIN
    INSERT INTO plannasaas.organisation_counter (organisation_id, counter, next_value)
    VALUES (p_organisation, p_counter, 2)
    ON CONFLICT (organisation_id, counter)
        DO UPDATE SET next_value = plannasaas.organisation_counter.next_value + 1
    RETURNING next_value - 1 INTO v_value;
    RETURN p_prefix || '-' || lpad(v_value::text, 6, '0');
END
$$;

COMMENT ON FUNCTION plannasaas.next_reference(uuid, text, text) IS
    'The next reference for an organisation, e.g. next_reference(org, ''project'', ''PRJ'') -> PRJ-000001. Row-locked, so gapless unless the transaction rolls back.';

-- -----------------------------------------------------------------------------
-- Clients and projects
-- -----------------------------------------------------------------------------

CREATE TABLE plannasaas.client (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id  uuid NOT NULL REFERENCES plannasaas.organisation (id) ON DELETE CASCADE,
    kind             text NOT NULL DEFAULT 'person' CHECK (kind IN ('person', 'company')),
    name             text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    email            text,
    phone            text,
    abn              text CHECK (abn ~ '^[0-9]{11}$'),
    billing_street   text,
    billing_suburb   text,
    billing_state    text CHECK (billing_state IN ('NSW', 'VIC', 'QLD', 'WA', 'SA', 'TAS', 'ACT', 'NT')),
    billing_postcode text CHECK (billing_postcode ~ '^[0-9]{4}$'),
    notes            text,
    archived_at      timestamptz,
    created_by       uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, id)
);

CREATE INDEX ix_client_org_name ON plannasaas.client (organisation_id, lower(name)) WHERE archived_at IS NULL;

COMMENT ON TABLE plannasaas.client IS 'Who the work is for: the homeowner or builder''s customer a quote goes to.';

CREATE TABLE plannasaas.project (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id   uuid NOT NULL REFERENCES plannasaas.organisation (id) ON DELETE CASCADE,
    reference         text NOT NULL,
    name              text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    description       text,
    client_id         uuid,
    status            text NOT NULL DEFAULT 'active'
                      CHECK (status IN ('active', 'on_hold', 'completed', 'cancelled')),
    lot_number        text,
    site_street       text,
    site_suburb       text,
    site_state        text NOT NULL DEFAULT 'VIC'
                      CHECK (site_state IN ('NSW', 'VIC', 'QLD', 'WA', 'SA', 'TAS', 'ACT', 'NT')),
    site_postcode     text CHECK (site_postcode ~ '^[0-9]{4}$'),
    distributor_code  text REFERENCES plannasaas.electricity_distributor (code),
    supply_phases     smallint NOT NULL DEFAULT 1 CHECK (supply_phases IN (1, 3)),
    due_on            date,
    created_by        uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    archived_at       timestamptz,
    UNIQUE (organisation_id, id),
    UNIQUE (organisation_id, reference),
    FOREIGN KEY (organisation_id, client_id) REFERENCES plannasaas.client (organisation_id, id)
);

CREATE INDEX ix_project_org_status ON plannasaas.project (organisation_id, status, updated_at DESC) WHERE archived_at IS NULL;
CREATE INDEX ix_project_client ON plannasaas.project (organisation_id, client_id) WHERE client_id IS NOT NULL;

COMMENT ON TABLE plannasaas.project IS 'A job at one site, for one client. Holds one or more house plans.';
COMMENT ON COLUMN plannasaas.project.reference IS 'Human-readable, unique per organisation: PRJ-000042. From plannasaas.next_reference.';

CREATE TABLE plannasaas.project_assignee (
    organisation_id uuid NOT NULL,
    project_id      uuid NOT NULL,
    user_id         uuid NOT NULL REFERENCES plannasaas.supabase_user (id) ON DELETE CASCADE,
    responsibility  text NOT NULL CHECK (responsibility IN ('lead', 'designer', 'electrician', 'estimator')),
    assigned_at     timestamptz NOT NULL DEFAULT now(),
    assigned_by     uuid,
    PRIMARY KEY (project_id, user_id, responsibility),
    FOREIGN KEY (organisation_id, project_id) REFERENCES plannasaas.project (organisation_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organisation_id, user_id) REFERENCES plannasaas.organisation_member (organisation_id, user_id) ON DELETE CASCADE
);

CREATE INDEX ix_project_assignee_user ON plannasaas.project_assignee (organisation_id, user_id);

COMMENT ON TABLE plannasaas.project_assignee IS
    'Who is working on a project and in what capacity. Every member of the organisation can still see it.';

-- -----------------------------------------------------------------------------
-- House plans, their levels and their lifecycle
-- -----------------------------------------------------------------------------

CREATE TABLE plannasaas.plan (
    id                           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id              uuid NOT NULL,
    project_id                   uuid NOT NULL,
    name                         text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    dwelling_type                text NOT NULL DEFAULT 'house'
                                 CHECK (dwelling_type IN ('house', 'townhouse', 'unit', 'granny_flat', 'extension', 'other')),
    storeys                      smallint NOT NULL DEFAULT 1 CHECK (storeys BETWEEN 1 AND 4),
    stage                        text NOT NULL DEFAULT 'awaiting_upload' REFERENCES plannasaas.plan_stage (code),
    stage_changed_at             timestamptz NOT NULL DEFAULT now(),
    current_electrical_design_id uuid,
    lock_version                 integer NOT NULL DEFAULT 0,
    created_by                   uuid,
    created_at                   timestamptz NOT NULL DEFAULT now(),
    updated_at                   timestamptz NOT NULL DEFAULT now(),
    archived_at                  timestamptz,
    UNIQUE (organisation_id, id),
    FOREIGN KEY (organisation_id, project_id) REFERENCES plannasaas.project (organisation_id, id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_plan_project_name ON plannasaas.plan (project_id, lower(name)) WHERE archived_at IS NULL;
CREATE INDEX ix_plan_org_stage ON plannasaas.plan (organisation_id, stage, stage_changed_at DESC) WHERE archived_at IS NULL;

COMMENT ON TABLE plannasaas.plan IS
    'One house design within a project (a project may have several: Lot 12 Type A, Lot 13 Type B). Carries the lifecycle stage.';
COMMENT ON COLUMN plannasaas.plan.lock_version IS
    'Optimistic locking: the API updates WHERE lock_version = :seen and increments it, so two editors cannot silently overwrite each other.';

CREATE TABLE plannasaas.plan_stage_event (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organisation_id uuid NOT NULL,
    plan_id         uuid NOT NULL,
    from_stage      text REFERENCES plannasaas.plan_stage (code),
    to_stage        text NOT NULL REFERENCES plannasaas.plan_stage (code),
    actor_id        uuid,
    note            text,
    occurred_at     timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (organisation_id, plan_id) REFERENCES plannasaas.plan (organisation_id, id) ON DELETE CASCADE
);

CREATE INDEX ix_plan_stage_event_plan ON plannasaas.plan_stage_event (plan_id, occurred_at);

COMMENT ON TABLE plannasaas.plan_stage_event IS
    'Every stage a plan has been in, who moved it and why. Written only by the triggers on plannasaas.plan.';

-- Validates every stage change against plannasaas.plan_stage_transition.
CREATE FUNCTION plannasaas.plan_check_stage() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.stage IS DISTINCT FROM OLD.stage THEN
        IF NOT EXISTS (SELECT 1 FROM plannasaas.plan_stage_transition
                        WHERE from_stage = OLD.stage AND to_stage = NEW.stage) THEN
            RAISE EXCEPTION 'A plan cannot move from % to %', OLD.stage, NEW.stage
                USING ERRCODE = 'check_violation', HINT = 'See plannasaas.plan_stage_transition.';
        END IF;
        NEW.stage_changed_at := now();
    END IF;
    RETURN NEW;
END
$$;

-- Records the stage a plan starts in, and every change after.
CREATE FUNCTION plannasaas.plan_record_stage() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.stage IS DISTINCT FROM OLD.stage THEN
        INSERT INTO plannasaas.plan_stage_event (organisation_id, plan_id, from_stage, to_stage, actor_id, note)
        VALUES (NEW.organisation_id, NEW.id,
                CASE WHEN TG_OP = 'UPDATE' THEN OLD.stage END,
                NEW.stage,
                plannasaas.current_actor_id(),
                nullif(current_setting('plannasaas.stage_note', true), ''));
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER plan_check_stage BEFORE UPDATE OF stage ON plannasaas.plan
    FOR EACH ROW EXECUTE FUNCTION plannasaas.plan_check_stage();
CREATE TRIGGER plan_record_stage AFTER INSERT OR UPDATE OF stage ON plannasaas.plan
    FOR EACH ROW EXECUTE FUNCTION plannasaas.plan_record_stage();

CREATE TABLE plannasaas.plan_level (
    id                            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id               uuid NOT NULL,
    plan_id                       uuid NOT NULL,
    name                          text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 60),
    ordinal                       smallint NOT NULL CHECK (ordinal BETWEEN -2 AND 5),
    ceiling_height_mm             integer NOT NULL DEFAULT 2550 CHECK (ceiling_height_mm BETWEEN 2100 AND 6000),
    current_floor_plan_version_id uuid,
    created_at                    timestamptz NOT NULL DEFAULT now(),
    updated_at                    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, id),
    UNIQUE (plan_id, ordinal),
    FOREIGN KEY (organisation_id, plan_id) REFERENCES plannasaas.plan (organisation_id, id) ON DELETE CASCADE
);

COMMENT ON TABLE plannasaas.plan_level IS
    'A storey of a plan, each with its own floor plan. Ordinal 0 is ground, 1 first floor, -1 basement.';

-- -----------------------------------------------------------------------------
-- Files
-- -----------------------------------------------------------------------------

CREATE TABLE plannasaas.stored_file (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id uuid NOT NULL REFERENCES plannasaas.organisation (id) ON DELETE CASCADE,
    purpose         text NOT NULL CHECK (purpose IN (
                        'floor_plan_source', 'floor_plan_export', 'electrical_drawing',
                        'quote_document', 'licence_document', 'attachment')),
    storage_backend text NOT NULL CHECK (storage_backend IN ('local', 's3', 'supabase_storage')),
    storage_key     text NOT NULL,
    original_name   text,
    content_type    text NOT NULL,
    byte_size       bigint NOT NULL CHECK (byte_size > 0),
    sha256          bytea NOT NULL CHECK (length(sha256) = 32),
    image_width_px  integer CHECK (image_width_px > 0),
    image_height_px integer CHECK (image_height_px > 0),
    uploaded_by     uuid,
    created_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz,
    UNIQUE (organisation_id, id),
    UNIQUE (storage_backend, storage_key)
);

CREATE INDEX ix_stored_file_org_hash ON plannasaas.stored_file (organisation_id, sha256) WHERE deleted_at IS NULL;

COMMENT ON TABLE plannasaas.stored_file IS
    'Metadata for every file the app keeps. The bytes live in object storage (or on disk in development) under storage_key.';
COMMENT ON COLUMN plannasaas.stored_file.sha256 IS 'Content hash: finds a plan uploaded twice, and proves a file has not changed.';

-- -----------------------------------------------------------------------------
-- Floor plans: analysis runs and versions
-- -----------------------------------------------------------------------------

CREATE TABLE plannasaas.analysis_run (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id   uuid NOT NULL,
    plan_level_id     uuid NOT NULL,
    source_file_id    uuid NOT NULL,
    status            text NOT NULL DEFAULT 'queued'
                      CHECK (status IN ('queued', 'running', 'succeeded', 'failed', 'cancelled')),
    analyser_version  text,
    parameters        jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(parameters) = 'object'),
    steps             jsonb NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(steps) = 'array'),
    warnings          jsonb NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(warnings) = 'array'),
    error_message     text,
    requested_by      uuid,
    queued_at         timestamptz NOT NULL DEFAULT now(),
    started_at        timestamptz,
    finished_at       timestamptz,
    UNIQUE (organisation_id, id),
    FOREIGN KEY (organisation_id, plan_level_id) REFERENCES plannasaas.plan_level (organisation_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organisation_id, source_file_id) REFERENCES plannasaas.stored_file (organisation_id, id),
    CHECK (finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at),
    CHECK (status <> 'failed' OR error_message IS NOT NULL)
);

CREATE INDEX ix_analysis_run_level ON plannasaas.analysis_run (plan_level_id, queued_at DESC);
CREATE INDEX ix_analysis_run_pending ON plannasaas.analysis_run (queued_at) WHERE status IN ('queued', 'running');

COMMENT ON TABLE plannasaas.analysis_run IS
    'One run of the floor-plan analyser over an uploaded image: what it was asked, what each step reported, how it ended.';

CREATE TABLE plannasaas.floor_plan_version (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id       uuid NOT NULL,
    plan_level_id         uuid NOT NULL,
    version_no            integer NOT NULL CHECK (version_no > 0),
    state                 text NOT NULL DEFAULT 'draft' CHECK (state IN ('draft', 'committed')),
    origin                text NOT NULL CHECK (origin IN ('analysis', 'editor', 'import')),
    based_on_version_id   uuid,
    analysis_run_id       uuid,
    source_file_id        uuid,
    document              jsonb NOT NULL CHECK (jsonb_typeof(document) = 'object'),
    document_schema       smallint NOT NULL DEFAULT 1,
    scale_mm_per_px       numeric(10, 4) CHECK (scale_mm_per_px > 0),
    scale_confidence      numeric(4, 3) CHECK (scale_confidence BETWEEN 0 AND 1),
    scale_method          text CHECK (scale_method IN ('ocr-dimensions', 'wall-thickness', 'manual', 'fallback')),
    room_count            integer NOT NULL DEFAULT 0 CHECK (room_count >= 0),
    wall_count            integer NOT NULL DEFAULT 0 CHECK (wall_count >= 0),
    opening_count         integer NOT NULL DEFAULT 0 CHECK (opening_count >= 0),
    floor_area_m2         numeric(10, 2) CHECK (floor_area_m2 >= 0),
    open_check_count      integer NOT NULL DEFAULT 0 CHECK (open_check_count >= 0),
    content_sha256        bytea CHECK (length(content_sha256) = 32),
    note                  text,
    lock_version          integer NOT NULL DEFAULT 0,
    created_by            uuid,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    committed_by          uuid,
    committed_at          timestamptz,
    UNIQUE (organisation_id, id),
    UNIQUE (plan_level_id, version_no),
    FOREIGN KEY (organisation_id, plan_level_id) REFERENCES plannasaas.plan_level (organisation_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organisation_id, based_on_version_id) REFERENCES plannasaas.floor_plan_version (organisation_id, id),
    FOREIGN KEY (organisation_id, analysis_run_id) REFERENCES plannasaas.analysis_run (organisation_id, id),
    FOREIGN KEY (organisation_id, source_file_id) REFERENCES plannasaas.stored_file (organisation_id, id),
    CHECK ((state = 'committed') = (committed_at IS NOT NULL)),
    CHECK (origin <> 'analysis' OR analysis_run_id IS NOT NULL)
);

-- One draft at a time per level: the one the editor is working on.
CREATE UNIQUE INDEX ux_floor_plan_one_draft ON plannasaas.floor_plan_version (plan_level_id) WHERE state = 'draft';

COMMENT ON TABLE plannasaas.floor_plan_version IS
    'A floor plan (the FloorPlan JSON the editor works on), versioned. The editor saves into the one draft; committing freezes it.';
COMMENT ON COLUMN plannasaas.floor_plan_version.document IS
    'The FloorPlan document in millimetres, as defined by contracts/floor-plan.schema.json. The source of truth for geometry.';
COMMENT ON COLUMN plannasaas.floor_plan_version.floor_area_m2 IS
    'Derived from document when saved, so lists and reports need not open the JSON.';

ALTER TABLE plannasaas.plan_level
    ADD FOREIGN KEY (organisation_id, current_floor_plan_version_id)
        REFERENCES plannasaas.floor_plan_version (organisation_id, id) DEFERRABLE INITIALLY DEFERRED;

-- Committed versions are history: they may not change or disappear (except
-- with the level they belong to).
CREATE FUNCTION plannasaas.protect_committed_version() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.state = 'committed' THEN
        IF TG_OP = 'DELETE' THEN
            -- Allowed only as part of deleting the parent (a cascade): the
            -- parent row is already gone when this fires. The parent column
            -- differs by table, so it is read dynamically.
            IF EXISTS (SELECT 1 WHERE (TG_TABLE_NAME = 'floor_plan_version'
                                       AND EXISTS (SELECT 1 FROM plannasaas.plan_level
                                                    WHERE id = (to_jsonb(OLD) ->> 'plan_level_id')::uuid))
                                   OR (TG_TABLE_NAME = 'electrical_design_version'
                                       AND EXISTS (SELECT 1 FROM plannasaas.plan
                                                    WHERE id = (to_jsonb(OLD) ->> 'plan_id')::uuid))) THEN
                RAISE EXCEPTION 'Committed version % cannot be deleted', OLD.id USING ERRCODE = 'check_violation';
            END IF;
            RETURN OLD;
        END IF;
        RAISE EXCEPTION 'Committed version % cannot be changed; save a new version instead', OLD.id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN COALESCE(NEW, OLD);
END
$$;

CREATE TRIGGER floor_plan_version_protect BEFORE UPDATE OR DELETE ON plannasaas.floor_plan_version
    FOR EACH ROW EXECUTE FUNCTION plannasaas.protect_committed_version();

-- -----------------------------------------------------------------------------
-- Electrical: rule packs, policies, briefs, design versions, bill of materials
-- -----------------------------------------------------------------------------

CREATE TABLE plannasaas.rule_pack (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code           text NOT NULL CHECK (code ~ '^[a-z0-9-]+$'),
    version        text NOT NULL,
    status         text NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'released', 'retired')),
    standards      jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(standards) = 'object'),
    content_sha256 bytea NOT NULL CHECK (length(content_sha256) = 32),
    signed_off_by  uuid REFERENCES plannasaas.electrical_licence (id),
    signed_off_at  timestamptz,
    released_at    timestamptz,
    notes          text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (code, version),
    CHECK (status <> 'released' OR (signed_off_by IS NOT NULL AND released_at IS NOT NULL))
);

COMMENT ON TABLE plannasaas.rule_pack IS
    'A version of the rules the electrical engine applies (shipped with the API). Released only once a licensed electrician has signed it off.';
COMMENT ON COLUMN plannasaas.rule_pack.standards IS 'The editions it encodes, e.g. {"AS/NZS 3000": "2018+A3", "VIC SIR": "2024"}.';

CREATE TABLE plannasaas.organisation_policy (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id uuid NOT NULL REFERENCES plannasaas.organisation (id) ON DELETE CASCADE,
    version         integer NOT NULL CHECK (version > 0),
    policy          jsonb NOT NULL CHECK (jsonb_typeof(policy) = 'object'),
    is_active       boolean NOT NULL DEFAULT false,
    created_by      uuid,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, id),
    UNIQUE (organisation_id, version)
);

CREATE UNIQUE INDEX ux_organisation_policy_active ON plannasaas.organisation_policy (organisation_id) WHERE is_active;

COMMENT ON TABLE plannasaas.organisation_policy IS
    'An organisation''s overrides of design-policy rules (switch heights, outlets per room...). Never mandatory rules.';

CREATE TABLE plannasaas.electrical_brief (
    plan_id                  uuid PRIMARY KEY,
    organisation_id          uuid NOT NULL,
    state                    text NOT NULL DEFAULT 'VIC'
                             CHECK (state IN ('NSW', 'VIC', 'QLD', 'WA', 'SA', 'TAS', 'ACT', 'NT')),
    distributor_code         text REFERENCES plannasaas.electricity_distributor (code),
    supply_phases            smallint NOT NULL DEFAULT 1 CHECK (supply_phases IN (1, 3)),
    consumer_mains_length_m  numeric(6, 1) CHECK (consumer_mains_length_m > 0),
    construction             jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(construction) = 'object'),
    appliances               jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(appliances) = 'object'),
    preferences              jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(preferences) = 'object'),
    lock_version             integer NOT NULL DEFAULT 0,
    updated_by               uuid,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (organisation_id, plan_id) REFERENCES plannasaas.plan (organisation_id, id) ON DELETE CASCADE
);

COMMENT ON TABLE plannasaas.electrical_brief IS
    'The project brief for a plan''s electrical design: supply, construction, appliances, preferences. Each design version keeps a snapshot.';

CREATE TABLE plannasaas.electrical_design_version (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id           uuid NOT NULL,
    plan_id                   uuid NOT NULL,
    version_no                integer NOT NULL CHECK (version_no > 0),
    state                     text NOT NULL DEFAULT 'draft' CHECK (state IN ('draft', 'committed')),
    origin                    text NOT NULL CHECK (origin IN ('engine', 'editor')),
    based_on_version_id       uuid,
    rule_pack_id              uuid NOT NULL REFERENCES plannasaas.rule_pack (id),
    organisation_policy_id    uuid,
    engine_version            text NOT NULL,
    brief                     jsonb NOT NULL CHECK (jsonb_typeof(brief) = 'object'),
    document                  jsonb NOT NULL CHECK (jsonb_typeof(document) = 'object'),
    document_schema           smallint NOT NULL DEFAULT 1,
    point_count               integer NOT NULL DEFAULT 0 CHECK (point_count >= 0),
    circuit_count             integer NOT NULL DEFAULT 0 CHECK (circuit_count >= 0),
    max_demand_a              numeric(7, 2) CHECK (max_demand_a >= 0),
    mandatory_violation_count integer NOT NULL DEFAULT 0 CHECK (mandatory_violation_count >= 0),
    warning_count             integer NOT NULL DEFAULT 0 CHECK (warning_count >= 0),
    decision_required_count   integer NOT NULL DEFAULT 0 CHECK (decision_required_count >= 0),
    content_sha256            bytea CHECK (length(content_sha256) = 32),
    note                      text,
    lock_version              integer NOT NULL DEFAULT 0,
    created_by                uuid,
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now(),
    committed_by              uuid,
    committed_at              timestamptz,
    UNIQUE (organisation_id, id),
    UNIQUE (organisation_id, plan_id, id),
    UNIQUE (plan_id, version_no),
    FOREIGN KEY (organisation_id, plan_id) REFERENCES plannasaas.plan (organisation_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organisation_id, based_on_version_id) REFERENCES plannasaas.electrical_design_version (organisation_id, id),
    FOREIGN KEY (organisation_id, organisation_policy_id) REFERENCES plannasaas.organisation_policy (organisation_id, id),
    CHECK ((state = 'committed') = (committed_at IS NOT NULL)),
    -- A design that breaks a mandatory rule can be worked on, never committed.
    CHECK (state <> 'committed' OR mandatory_violation_count = 0)
);

CREATE UNIQUE INDEX ux_electrical_design_one_draft ON plannasaas.electrical_design_version (plan_id) WHERE state = 'draft';

COMMENT ON TABLE plannasaas.electrical_design_version IS
    'An electrical design (the ElectricalDesign JSON), versioned like floor plans. Records the rule pack, policy and engine that made it, so it can be reproduced.';

CREATE TRIGGER electrical_design_version_protect BEFORE UPDATE OR DELETE ON plannasaas.electrical_design_version
    FOR EACH ROW EXECUTE FUNCTION plannasaas.protect_committed_version();

ALTER TABLE plannasaas.plan
    ADD FOREIGN KEY (organisation_id, current_electrical_design_id)
        REFERENCES plannasaas.electrical_design_version (organisation_id, id) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE plannasaas.electrical_design_input (
    organisation_id       uuid NOT NULL,
    design_version_id     uuid NOT NULL,
    floor_plan_version_id uuid NOT NULL,
    PRIMARY KEY (design_version_id, floor_plan_version_id),
    FOREIGN KEY (organisation_id, design_version_id)
        REFERENCES plannasaas.electrical_design_version (organisation_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organisation_id, floor_plan_version_id) REFERENCES plannasaas.floor_plan_version (organisation_id, id)
);

COMMENT ON TABLE plannasaas.electrical_design_input IS
    'The exact floor-plan versions (one per level) an electrical design was made from.';

CREATE TABLE plannasaas.bom_line (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id   uuid NOT NULL,
    design_version_id uuid NOT NULL,
    item_code         text NOT NULL,
    description       text NOT NULL,
    category          text NOT NULL,
    quantity          numeric(12, 3) NOT NULL CHECK (quantity > 0),
    unit              text NOT NULL CHECK (unit IN ('each', 'm', 'set', 'pack')),
    spec              jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(spec) = 'object'),
    UNIQUE (design_version_id, item_code),
    FOREIGN KEY (organisation_id, design_version_id)
        REFERENCES plannasaas.electrical_design_version (organisation_id, id) ON DELETE CASCADE
);

COMMENT ON TABLE plannasaas.bom_line IS
    'The bill of materials of a design version: what to buy, in what quantity. Derived from the design; the basis of quotes and stock checks.';

-- -----------------------------------------------------------------------------
-- Review by a licensed electrician
-- -----------------------------------------------------------------------------

CREATE TABLE plannasaas.review (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL,
    plan_id             uuid NOT NULL,
    design_version_id   uuid NOT NULL,
    status              text NOT NULL DEFAULT 'requested'
                        CHECK (status IN ('requested', 'in_progress', 'approved', 'changes_requested', 'cancelled')),
    requested_by        uuid NOT NULL,
    reviewer_id         uuid,
    reviewer_licence_id uuid REFERENCES plannasaas.electrical_licence (id),
    licence_snapshot    jsonb CHECK (licence_snapshot IS NULL OR jsonb_typeof(licence_snapshot) = 'object'),
    due_on              date,
    decision_note       text,
    decided_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, id),
    FOREIGN KEY (organisation_id, plan_id) REFERENCES plannasaas.plan (organisation_id, id) ON DELETE CASCADE,
    FOREIGN KEY (organisation_id, design_version_id) REFERENCES plannasaas.electrical_design_version (organisation_id, id),
    FOREIGN KEY (organisation_id, reviewer_id) REFERENCES plannasaas.organisation_member (organisation_id, user_id),
    CHECK ((status IN ('approved', 'changes_requested')) = (decided_at IS NOT NULL)),
    -- An approval is always signed under a licence, recorded as it stood.
    CHECK (status <> 'approved'
           OR (reviewer_id IS NOT NULL AND reviewer_licence_id IS NOT NULL AND licence_snapshot IS NOT NULL))
);

CREATE UNIQUE INDEX ux_review_one_open ON plannasaas.review (design_version_id) WHERE status IN ('requested', 'in_progress');
CREATE INDEX ix_review_reviewer_open ON plannasaas.review (organisation_id, reviewer_id) WHERE status IN ('requested', 'in_progress');

COMMENT ON TABLE plannasaas.review IS
    'A licensed electrician''s review of one committed design version. Approval is the sign-off the plan''s lifecycle waits for.';

-- Reviews are only of committed designs: a draft can change under the reviewer.
CREATE FUNCTION plannasaas.review_requires_committed_design() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM plannasaas.electrical_design_version
                    WHERE id = NEW.design_version_id AND state = 'committed') THEN
        RAISE EXCEPTION 'Only a committed design version can be reviewed' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER review_requires_committed_design BEFORE INSERT OR UPDATE OF design_version_id ON plannasaas.review
    FOR EACH ROW EXECUTE FUNCTION plannasaas.review_requires_committed_design();

CREATE TABLE plannasaas.review_finding (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id uuid NOT NULL,
    review_id       uuid NOT NULL,
    item_ref        text,
    outcome         text NOT NULL CHECK (outcome IN ('kept', 'moved', 'removed', 'added', 'changed', 'comment')),
    severity        text NOT NULL DEFAULT 'info' CHECK (severity IN ('info', 'minor', 'major', 'blocking')),
    body            text,
    anchor          jsonb CHECK (anchor IS NULL OR jsonb_typeof(anchor) = 'object'),
    resolved_at     timestamptz,
    created_by      uuid NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (organisation_id, review_id) REFERENCES plannasaas.review (organisation_id, id) ON DELETE CASCADE
);

CREATE INDEX ix_review_finding_review ON plannasaas.review_finding (review_id, created_at);

COMMENT ON TABLE plannasaas.review_finding IS
    'What the reviewer did with each item (kept, moved, removed, added) and any comment. Feeds the "90% accepted unchanged" measure.';
COMMENT ON COLUMN plannasaas.review_finding.item_ref IS 'The id of a point or circuit in the design document, e.g. lt_001 or c_L1.';

-- -----------------------------------------------------------------------------
-- Catalogue, prices and quotes
-- -----------------------------------------------------------------------------

CREATE TABLE plannasaas.catalogue_item (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id uuid REFERENCES plannasaas.organisation (id) ON DELETE CASCADE,
    item_code       text NOT NULL CHECK (item_code ~ '^[A-Z0-9][A-Z0-9.-]*$'),
    name            text NOT NULL,
    category        text NOT NULL CHECK (category IN (
                        'luminaire', 'switch', 'outlet', 'protection', 'switchboard', 'cable',
                        'smoke_alarm', 'fan', 'data', 'sundry', 'labour')),
    unit            text NOT NULL CHECK (unit IN ('each', 'm', 'set', 'pack', 'hour')),
    spec            jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(spec) = 'object'),
    is_active       boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE NULLS NOT DISTINCT (organisation_id, item_code)
);

COMMENT ON TABLE plannasaas.catalogue_item IS
    'Items a design can use and a quote can price. organisation_id NULL is the platform catalogue; an organisation may add its own.';

CREATE TABLE plannasaas.price_list (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id uuid NOT NULL REFERENCES plannasaas.organisation (id) ON DELETE CASCADE,
    name            text NOT NULL,
    currency        char(3) NOT NULL DEFAULT 'AUD' CHECK (currency ~ '^[A-Z]{3}$'),
    valid_from      date,
    valid_to        date,
    is_default      boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, id),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);

CREATE UNIQUE INDEX ux_price_list_default ON plannasaas.price_list (organisation_id) WHERE is_default;

CREATE TABLE plannasaas.price_list_item (
    organisation_id   uuid NOT NULL,
    price_list_id     uuid NOT NULL,
    item_code         text NOT NULL,
    unit_cost_ex_gst  numeric(12, 2) CHECK (unit_cost_ex_gst >= 0),
    unit_price_ex_gst numeric(12, 2) NOT NULL CHECK (unit_price_ex_gst >= 0),
    labour_minutes    numeric(8, 2) CHECK (labour_minutes >= 0),
    PRIMARY KEY (price_list_id, item_code),
    FOREIGN KEY (organisation_id, price_list_id) REFERENCES plannasaas.price_list (organisation_id, id) ON DELETE CASCADE
);

COMMENT ON TABLE plannasaas.price_list_item IS
    'What an organisation pays (cost) and charges (price) for an item, and the labour it takes to install.';

CREATE TABLE plannasaas.quote (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id     uuid NOT NULL,
    plan_id             uuid NOT NULL,
    design_version_id   uuid NOT NULL,
    client_id           uuid,
    reference           text NOT NULL,
    revision            smallint NOT NULL DEFAULT 1 CHECK (revision > 0),
    status              text NOT NULL DEFAULT 'draft'
                        CHECK (status IN ('draft', 'sent', 'accepted', 'declined', 'expired', 'superseded', 'withdrawn')),
    currency            char(3) NOT NULL DEFAULT 'AUD' CHECK (currency ~ '^[A-Z]{3}$'),
    gst_rate            numeric(5, 4) NOT NULL DEFAULT 0.1000 CHECK (gst_rate BETWEEN 0 AND 1),
    subtotal_ex_gst     numeric(12, 2) NOT NULL DEFAULT 0,
    gst_amount          numeric(12, 2) NOT NULL DEFAULT 0,
    total_inc_gst       numeric(12, 2) NOT NULL DEFAULT 0,
    price_list_id       uuid,
    client_snapshot     jsonb CHECK (client_snapshot IS NULL OR jsonb_typeof(client_snapshot) = 'object'),
    terms               text,
    valid_until         date,
    supersedes_quote_id uuid,
    sent_at             timestamptz,
    decided_at          timestamptz,
    lock_version        integer NOT NULL DEFAULT 0,
    created_by          uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, id),
    UNIQUE (organisation_id, reference, revision),
    FOREIGN KEY (organisation_id, plan_id) REFERENCES plannasaas.plan (organisation_id, id),
    -- The design priced must be one of this plan's designs.
    FOREIGN KEY (organisation_id, plan_id, design_version_id)
        REFERENCES plannasaas.electrical_design_version (organisation_id, plan_id, id),
    FOREIGN KEY (organisation_id, client_id) REFERENCES plannasaas.client (organisation_id, id),
    FOREIGN KEY (organisation_id, price_list_id) REFERENCES plannasaas.price_list (organisation_id, id),
    FOREIGN KEY (organisation_id, supersedes_quote_id) REFERENCES plannasaas.quote (organisation_id, id),
    CHECK (total_inc_gst = subtotal_ex_gst + gst_amount),
    CHECK (status NOT IN ('sent', 'accepted', 'declined', 'expired') OR sent_at IS NOT NULL),
    CHECK ((status IN ('accepted', 'declined')) = (decided_at IS NOT NULL))
);

CREATE INDEX ix_quote_org_status ON plannasaas.quote (organisation_id, status, updated_at DESC);
CREATE INDEX ix_quote_plan ON plannasaas.quote (plan_id, created_at DESC);

-- One live quote per house: a revision supersedes the one before it.
CREATE UNIQUE INDEX ux_quote_one_live_per_plan ON plannasaas.quote (plan_id) WHERE status IN ('draft', 'sent');

COMMENT ON TABLE plannasaas.quote IS
    'A priced offer to a client for one house plan, pricing one committed design version of it. Revisions keep the reference (Q-000107 rev 2) and supersede the one before. Totals are kept by trigger from the lines.';

-- Only a committed design is priced: a draft can change under the quote.
CREATE FUNCTION plannasaas.quote_requires_committed_design() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM plannasaas.electrical_design_version
                    WHERE id = NEW.design_version_id AND state = 'committed') THEN
        RAISE EXCEPTION 'Only a committed design version can be quoted' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER quote_requires_committed_design BEFORE INSERT OR UPDATE OF design_version_id ON plannasaas.quote
    FOR EACH ROW EXECUTE FUNCTION plannasaas.quote_requires_committed_design();

CREATE TABLE plannasaas.quote_line (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id   uuid NOT NULL,
    quote_id          uuid NOT NULL,
    line_no           integer NOT NULL CHECK (line_no > 0),
    kind              text NOT NULL CHECK (kind IN ('material', 'labour', 'other', 'discount')),
    item_code         text,
    description       text NOT NULL,
    quantity          numeric(12, 3) NOT NULL CHECK (quantity > 0),
    unit              text NOT NULL CHECK (unit IN ('each', 'm', 'set', 'pack', 'hour', 'lot')),
    unit_price_ex_gst numeric(12, 2) NOT NULL,
    line_total_ex_gst numeric(12, 2) GENERATED ALWAYS AS (round(quantity * unit_price_ex_gst, 2)) STORED,
    source            text NOT NULL DEFAULT 'manual' CHECK (source IN ('bom', 'manual')),
    UNIQUE (quote_id, line_no),
    FOREIGN KEY (organisation_id, quote_id) REFERENCES plannasaas.quote (organisation_id, id) ON DELETE CASCADE,
    CHECK ((kind = 'discount') = (unit_price_ex_gst < 0) OR unit_price_ex_gst = 0)
);

CREATE INDEX ix_quote_line_quote ON plannasaas.quote_line (quote_id, line_no);

-- Lines change only while a quote is a draft; totals always follow the lines.
CREATE FUNCTION plannasaas.quote_line_guard() RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    v_status text;
BEGIN
    SELECT status INTO v_status FROM plannasaas.quote WHERE id = COALESCE(NEW.quote_id, OLD.quote_id);
    -- No quote row: it is being deleted, and its lines with it.
    IF v_status IS NOT NULL AND v_status <> 'draft' THEN
        RAISE EXCEPTION 'Quote lines can only change while the quote is a draft (it is %)', v_status
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN COALESCE(NEW, OLD);
END
$$;

CREATE FUNCTION plannasaas.quote_recalculate() RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    v_quote uuid := COALESCE(NEW.quote_id, OLD.quote_id);
BEGIN
    UPDATE plannasaas.quote q
       SET subtotal_ex_gst = t.subtotal,
           gst_amount      = round(t.subtotal * q.gst_rate, 2),
           total_inc_gst   = t.subtotal + round(t.subtotal * q.gst_rate, 2)
      FROM (SELECT COALESCE(sum(line_total_ex_gst), 0) AS subtotal
              FROM plannasaas.quote_line WHERE quote_id = v_quote) t
     WHERE q.id = v_quote;
    RETURN NULL;
END
$$;

CREATE TRIGGER quote_line_guard BEFORE INSERT OR UPDATE OR DELETE ON plannasaas.quote_line
    FOR EACH ROW EXECUTE FUNCTION plannasaas.quote_line_guard();
CREATE TRIGGER quote_line_recalculate AFTER INSERT OR UPDATE OR DELETE ON plannasaas.quote_line
    FOR EACH ROW EXECUTE FUNCTION plannasaas.quote_recalculate();

-- A changed GST rate re-derives the totals; a quote that has gone to a client
-- is a record and is never deleted (withdraw or supersede it instead).
CREATE FUNCTION plannasaas.quote_guard() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'draft' THEN
            RAISE EXCEPTION 'Quote % has been sent and cannot be deleted; withdraw it instead', OLD.reference
                USING ERRCODE = 'check_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.gst_rate IS DISTINCT FROM OLD.gst_rate THEN
        NEW.gst_amount    := round(NEW.subtotal_ex_gst * NEW.gst_rate, 2);
        NEW.total_inc_gst := NEW.subtotal_ex_gst + NEW.gst_amount;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER quote_guard BEFORE UPDATE OR DELETE ON plannasaas.quote
    FOR EACH ROW EXECUTE FUNCTION plannasaas.quote_guard();

-- -----------------------------------------------------------------------------
-- Links to other systems (Planna One ERP) and the audit trail
-- -----------------------------------------------------------------------------

CREATE TABLE plannasaas.external_reference (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id uuid NOT NULL REFERENCES plannasaas.organisation (id) ON DELETE CASCADE,
    system          text NOT NULL CHECK (system IN ('planna_one')),
    entity_type     text NOT NULL CHECK (entity_type IN ('client', 'project', 'quote', 'catalogue_item')),
    entity_id       uuid NOT NULL,
    external_id     text NOT NULL,
    data            jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(data) = 'object'),
    synced_at       timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (organisation_id, system, entity_type, entity_id),
    UNIQUE (organisation_id, system, entity_type, external_id)
);

COMMENT ON TABLE plannasaas.external_reference IS
    'This app''s row <-> the same thing in another system, e.g. a quote and its Planna One sales quote, a catalogue item and its SKU.';

CREATE TABLE plannasaas.audit_event (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organisation_id uuid NOT NULL,
    actor_id        uuid,
    entity_type     text NOT NULL,
    entity_id       uuid,
    action          text NOT NULL,
    changes         jsonb CHECK (changes IS NULL OR jsonb_typeof(changes) = 'object'),
    request_id      text,
    occurred_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_event_org_time ON plannasaas.audit_event (organisation_id, occurred_at DESC);
CREATE INDEX ix_audit_event_entity ON plannasaas.audit_event (entity_type, entity_id, occurred_at DESC);

COMMENT ON TABLE plannasaas.audit_event IS
    'Append-only record of who did what. Written by the API. Partition by month (on occurred_at) when it grows.';

-- -----------------------------------------------------------------------------
-- updated_at triggers
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'organisation', 'organisation_member', 'user_profile', 'electrical_licence', 'client', 'project',
        'plan', 'plan_level', 'floor_plan_version', 'electrical_brief', 'electrical_design_version',
        'review', 'catalogue_item', 'price_list', 'quote']
    LOOP
        EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE ON plannasaas.%I FOR EACH ROW EXECUTE FUNCTION plannasaas.touch_updated_at()',
                       t || '_touch_updated_at', t);
    END LOOP;
END
$$;

-- -----------------------------------------------------------------------------
-- Row-level security: each organisation sees only its own rows
-- -----------------------------------------------------------------------------
-- FORCE applies the policies to the table owner too, which is the role the
-- API connects as, so a query that forgets its organisation finds nothing
-- rather than everything.

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'organisation_member', 'organisation_invitation', 'organisation_counter', 'client', 'project',
        'project_assignee', 'plan', 'plan_stage_event', 'plan_level', 'stored_file', 'analysis_run',
        'floor_plan_version', 'organisation_policy', 'electrical_brief', 'electrical_design_version',
        'electrical_design_input', 'bom_line', 'review', 'review_finding', 'price_list', 'price_list_item',
        'quote', 'quote_line', 'external_reference', 'audit_event']
    LOOP
        EXECUTE format('ALTER TABLE plannasaas.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE plannasaas.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON plannasaas.%I USING (organisation_id = plannasaas.current_organisation_id()) '
            'WITH CHECK (organisation_id = plannasaas.current_organisation_id())', t);
    END LOOP;
END
$$;

-- People can always read their own memberships, in every organisation, so the
-- app can list the organisations someone may switch to. Writing still needs
-- the organisation to be the current one.
CREATE POLICY own_memberships ON plannasaas.organisation_member FOR SELECT
    USING (user_id = plannasaas.current_actor_id());

-- An organisation is visible to itself, and to its members for the switcher.
ALTER TABLE plannasaas.organisation ENABLE ROW LEVEL SECURITY;
ALTER TABLE plannasaas.organisation FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON plannasaas.organisation
    USING (id = plannasaas.current_organisation_id())
    WITH CHECK (id = plannasaas.current_organisation_id());
CREATE POLICY member_organisations ON plannasaas.organisation FOR SELECT
    USING (EXISTS (SELECT 1 FROM plannasaas.organisation_member m
                    WHERE m.organisation_id = organisation.id AND m.user_id = plannasaas.current_actor_id()));

-- The platform catalogue (organisation_id NULL) is shared and read-only to
-- organisations; only a transaction that sets plannasaas.platform_admin = 'on' (a
-- migration, an operator tool) may write it.
CREATE FUNCTION plannasaas.is_platform_admin() RETURNS boolean
    LANGUAGE sql STABLE
AS $$ SELECT coalesce(current_setting('plannasaas.platform_admin', true), '') = 'on' $$;

ALTER TABLE plannasaas.catalogue_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE plannasaas.catalogue_item FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON plannasaas.catalogue_item
    USING (organisation_id IS NULL OR organisation_id = plannasaas.current_organisation_id() OR plannasaas.is_platform_admin())
    WITH CHECK (organisation_id = plannasaas.current_organisation_id()
                OR (organisation_id IS NULL AND plannasaas.is_platform_admin()));
