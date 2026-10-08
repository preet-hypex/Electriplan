-- The login the Electriplan API connects as: electriplan_api, a member of
-- electriplan_app (the role migration V4 grants data access to).
--
-- Run by an administrator of the database (locally, the db-roles service in
-- docker-compose.yml does it before the API starts; in CI, a workflow step):
--
--   psql -v api_password='...' -f infra/postgres/application-login.sql
--
-- Safe to run again: it creates what is missing and resets the password.
\set ON_ERROR_STOP on
SET client_min_messages = warning;

-- The group role, in case this runs before the first migration has.
SELECT 'CREATE ROLE electriplan_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOINHERIT'
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'electriplan_app')
\gexec

SELECT format('CREATE ROLE electriplan_api LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE INHERIT PASSWORD %L', :'api_password')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'electriplan_api')
\gexec

ALTER ROLE electriplan_api WITH LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE INHERIT PASSWORD :'api_password';
GRANT electriplan_app TO electriplan_api;
COMMENT ON ROLE electriplan_api IS 'The Electriplan API''s login. Works as electriplan_app; never owns anything.';
