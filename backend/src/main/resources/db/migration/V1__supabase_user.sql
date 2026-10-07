CREATE TABLE plannasaas.supabase_user (
    id                 uuid PRIMARY KEY,
    email              text,
    phone              text,
    user_metadata      jsonb NOT NULL DEFAULT '{}',
    app_metadata       jsonb NOT NULL DEFAULT '{}',
    email_confirmed_at timestamptz,
    invited_at         timestamptz,
    last_sign_in_at    timestamptz,
    banned_until       timestamptz,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz,
    copied_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_supabase_user_email_lower ON plannasaas.supabase_user (lower(email)) WHERE email IS NOT NULL;

COMMENT ON TABLE plannasaas.supabase_user IS
    'Read-only copy of Supabase Auth users (auth.users). Supabase is the source of truth; only the API''s sync writes here.';
COMMENT ON COLUMN plannasaas.supabase_user.id IS 'The Supabase user id: auth.users.id and the access token''s sub.';
COMMENT ON COLUMN plannasaas.supabase_user.copied_at IS 'When a change in Supabase was last copied into this row.';
