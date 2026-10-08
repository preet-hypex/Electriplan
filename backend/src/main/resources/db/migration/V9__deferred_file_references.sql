-- =============================================================================
-- V9: a company with uploaded files can be deleted (Epic P, story P5).
--
-- Deleting a company cascades two ways at once: straight to its files
-- (stored_file), and through its projects, houses and storeys to the analysis
-- runs and floor-plan versions that point at those files. Postgres checked
-- those pointers while the cascade was still under way, and refused. Checked
-- at the end of the transaction instead, every row is gone by then. Nothing
-- else changes: a pointer to a file that does not exist is still refused.
-- =============================================================================

ALTER TABLE electriplan.analysis_run
    ALTER CONSTRAINT analysis_run_organisation_id_source_file_id_fkey DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE electriplan.floor_plan_version
    ALTER CONSTRAINT floor_plan_version_organisation_id_source_file_id_fkey DEFERRABLE INITIALLY DEFERRED,
    ALTER CONSTRAINT floor_plan_version_organisation_id_analysis_run_id_fkey DEFERRABLE INITIALLY DEFERRED;

-- Designs record the floor-plan versions they were made from; both go when a house does.
ALTER TABLE electriplan.electrical_design_input
    ALTER CONSTRAINT electrical_design_input_organisation_id_floor_plan_version_fkey DEFERRABLE INITIALLY DEFERRED;
