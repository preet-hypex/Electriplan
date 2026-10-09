# Projects workspace — implementation plan (Epic P)

A company's work lives in **projects** and their **houses**. From the welcome page a person creates a
project, adds a house, uploads and edits its floor plan, and later runs the electrical design. Every
step is saved, so coming back next time opens their work where they left it, never a blank editor.

Status: **in progress**: P1–P7 are done: projects, houses, each house's floor plan saved as you edit with versions, uploads kept in S3, and house stages that follow the work. The database already has the whole hierarchy
([database-schema.md](database-schema.md), migration V2); until P1, no API or screen used it, and
the floor-plan editor saved nothing.

---

## 1. Decisions

| Question | Decision |
|---|---|
| How much of the hierarchy do people see? | **Project → House.** Every house has storeys underneath (`plan_level`); v1 creates the ground floor with the house and opens straight onto it. "Add a storey" comes later; nothing has to change for it |
| Clients | An **optional** field on a project (P9), not a level people must go through |
| How is a floor plan saved? | **Autosave plus versions.** The draft saves itself while you edit, so nothing is lost. **Save version** freezes a read-only copy with an optional note; designs, reviews and quotes point at versions. Editing after that starts a new draft from it |
| What is the welcome page? | **The company's project list**: newest activity first, searchable, each project with its houses' stages, plus **New project** and **Continue where you left off** |
| Where are uploaded images kept? | **In S3, per company** (`stored_file`), not per person, so everyone in the company sees a house's plan. The Java API does all the S3 work; the analyser only analyses. Locally an S3-compatible store in Docker Compose |
| What do people call a "plan"? | A **house** in the app and the API. `plan` stays the table's name; [database-schema.md](database-schema.md) explains the mapping |
| Which state is a project in? | **Always chosen**, never defaulted (the database default of `VIC` is removed). It decides which rules design it ([engine plan, "Expanding beyond Victoria"](electrical-engine-plan.md#expanding-beyond-victoria)) |

## 2. The hierarchy

```
Company (organisation)
 └─ Project            a job at one site: reference (PRJ-000042), name, site address, state,
     │                 distributor, supply phases, status, due date
     └─ House (plan)   one house design: name, dwelling type, stage
         └─ Level      a storey; v1: one, "Ground floor", created with the house
             └─ Floor plan versions      one editable draft + committed versions (FloorPlan JSON),
                │                        with the uploaded image (stored_file) and the analyser run
         └─ Electrical design versions   the engine's output, stored the same way (P8)
         └─ Quotes                       later (E17)
```

Everything is limited to the company the request acts in (T3: `X-Organisation-Id`, row-level
security) and checked against the role's permissions (T4):

| Action | Permission | Roles (today's matrix) |
|---|---|---|
| See projects, houses, floor plans | `company.view` | everyone |
| Create and edit projects and houses, archive them | `project.edit` | owner, admin, builder |
| Upload, analyse and edit floor plans, save versions | `floor-plan.edit` | owner, admin, builder, electrician |
| Run and edit electrical designs | `design.edit` | owner, admin, builder, electrician |

## 3. Stories

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| P1 | ✅ **Done** — **Projects and houses API** ([api.md](api.md#projects-and-houses)) | Create, list (search, status filter, newest activity first, paged), open, edit, archive and restore projects; create, open, edit, archive and restore houses (a house is created with its ground floor). References `PRJ-000001` come from the database, gapless per company. State is required; a distributor must be in the project's state. Field errors come back as `{"message", "errors": [{"field", "message"}]}`. Another company's project is a 404, never a 403. Every endpoint permission-checked; in the OpenAPI document | M |
| P2 | ✅ **Done** — Welcome page = project list | The project list from P1 with search and status filter; each row shows reference, name, suburb, state and its houses' stages; **New project** form (name, site address, state, distributor from the state's list); **Continue where you left off** lists the houses this person edited most recently. Empty state for a new company. Buttons hidden when the role lacks `project.edit` | M |
| P3 | ✅ **Done** (houses listed and added; editing a house's name, and its stage history, come with P6/P7) — Project page | The project's details (editable with `project.edit`), its houses with stage and last change, **Add house**; archive/restore; breadcrumbs Company › Project › House | M |
| P4 | ✅ **Done** — Floor-plan drafts and versions API ([api.md](api.md#floor-plans)) | For a house's level: get the current draft (or latest version); save the draft (autosave) with optimistic locking (`lock_version`; a stale save is a 409 saying who saved since); save a version (commit, with note); list versions; open a version; restore a version as the new draft. Every document checked by `FloorPlanReader`; summary columns (rooms, walls, area, open checks) and content hash derived on save. Committed versions are read-only (the database enforces it) | L |
| P5 | ✅ **Done** (files in S3 through the Java API; the analyser only analyses) — Uploads and analysis per house | Upload an image for a house: stored once per company (`stored_file`, deduplicated by hash), analysed (`analysis_run` with parameters, warnings, timings), and the result saved as the house's draft (`origin = analysis`). The image is served back through the API to members of that company only. The analyser stops keeping files per person | L |
| P6 | ✅ **Done** (the image behind a plan is still the analyser's, per person, until P5) — Editor opens a house | `/projects/:id/houses/:id/floor-plan` opens the draft; edits autosave (debounced, "Saving… / Saved / Not saved — retry"); conflict dialog on 409; **Save version** and a versions panel; leaving with unsaved changes warns. The current `/floor-plan` page becomes "open a house" | L |
| P7 | ✅ **Done** — House stages follow the work | Uploading → `analysing`; analysis done → `floor_plan_review`; **Approve floor plan** (commits a version) → `floor_plan_approved`; moves only as `plan_stage_transition` allows; stage history on the house page | M |
| P8 | Electrical design output per house | The engine's `ElectricalDesign` saved per house as draft + versions (`electrical_design_version`), recording the floor-plan versions, brief, rule pack and engine version it was made from (`electrical_design_input`); reopening shows the last design. Wired when the engine runs from the editor (E14-S2) | M |
| P9 | Clients | Optional client on a project: pick an existing one or create inline (name, email, phone); a client's projects. Not required anywhere | S |
| P10 | Project people | Assign members to a project (`project_assignee`: lead, designer, electrician, estimator); "my projects" filter on the welcome page | S |

**Order:** P1 → P2, P3 (UI on P1) → P4 → P5, P6 → P7 → P8 (with E14-S2) → P9, P10 any time.

## 4. P1 in detail

| Endpoint | Permission | Returns |
|---|---|---|
| `GET /api/projects?q=&status=&archived=&page=&size=` | `company.view` | A page of projects, newest activity first: reference, name, suburb, state, status, house count, houses by stage, updated |
| `POST /api/projects` | `project.edit` | 201 with the project (reference assigned) |
| `GET /api/projects/{id}` | `company.view` | The project with its houses |
| `PATCH /api/projects/{id}` | `project.edit` | The project; fields given are changed |
| `POST /api/projects/{id}/archive`, `/restore` | `project.edit` | The project |
| `POST /api/projects/{id}/houses` | `project.edit` | 201 with the house (stage `awaiting_upload`, ground floor created) |
| `GET /api/houses/{id}` | `company.view` | The house with its project's reference and name, levels, stage |
| `PATCH /api/houses/{id}` | `project.edit` | The house |
| `POST /api/houses/{id}/archive`, `/restore` | `project.edit` | The house |

Notes:

- **Archiving, not deleting.** Archived projects and houses leave the default lists and come back
  with restore. Deletion waits for the 90-day retention rules (T11).
- **References** come from a database trigger (`next_reference`, row-locked, gapless per company),
  read back by Hibernate after insert, so the API has no plain SQL for them.
- **Validation** happens twice: in the API, so errors name the field in plain words, and in the
  database (checks, foreign keys, row-level security), which holds whatever the code path.
- **Not found:** row-level security hides other companies' rows, so their ids are simply not
  found (404). The response never reveals that the id exists elsewhere.
