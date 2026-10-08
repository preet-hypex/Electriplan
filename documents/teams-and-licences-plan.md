# Teams, licences and roles — implementation plan

A company buys an Electriplan licence with a fixed number of seats, and its people work together under
it, each with a role that decides what they may do. One company never sees another's data.

Status: **in progress** — T1 (seats and licence period), T2 (the API's database role) and T3 (company context per request) done. The database already has most of this (see
[database-schema.md](database-schema.md)); the API does not enforce any of it yet: today any person
Supabase signs in can call every endpoint.

---

## 1. Decisions

| Question | Decision |
|---|---|
| How is a licence sold? | **Per company, with a fixed number of seats** (e.g. 5 people). One licence, one seat count, one renewal date |
| Roles | **Owner, admin, builder, electrician, viewer** — one role per person per company |
| Can people from another company see or review a design? | **No, never.** No external reviewers. Only the company's own electricians review its designs |
| Who creates a company and sets its seats? | **Electriplan (the platform operator)**, when the licence is sold. Public sign-up stays off. Self-service purchase can come later without changing the model |
| Do viewers use a seat? | **No — viewers are free**, so a company can show plans to homeowners or site staff without buying seats. Only roles that change things (owner, admin, builder, electrician) use seats |
| Trial | **3 seats for 14 days**, then the company must hold an active licence |
| Data after a licence closes | **Kept 90 days**, then deleted |
| Can one person belong to two companies? | Yes (e.g. an electrician contracting for two builders), with a separate role in each. Each request acts in exactly one company, and nothing crosses between them |

## 2. What already exists, and what is missing

| | Database (built) | API (missing) |
|---|---|---|
| Companies | `organisation`: name, ABN, `status` (trial / active / suspended / closed), `subscription_plan` | Creating one; reading its licence |
| Seats | — **no seat count yet** | Counting and enforcing |
| Members and roles | `organisation_member`: user × company × role | Membership check on every request; permissions per role |
| Invitations | `organisation_invitation`: email, role, token hash, expiry | Invite, accept, revoke, resend |
| Isolation | Row-level security on every company table (tested) | Setting the company per request; connecting as a role security applies to |
| Project staffing | `project_assignee` | Assigning people |
| Electrician licences | `electrical_licence`; reviews signed under one | Recording and verifying licences |

## 3. Licences and seats

New columns on `organisation` (migration V3):

| Column | Meaning |
|---|---|
| `seat_limit` | How many people the licence covers. Set by the operator |
| `licence_starts_on`, `licence_ends_on` | The licence period. After the end date the company becomes read-only (below) |
| `closed_at` | When the licence was closed; data is deleted 90 days later |

**What uses a seat:** every **active member** and every **pending invitation** whose role is owner,
admin, builder or electrician (so a company cannot invite past its limit and let the invitations
race). **Viewers never use a seat**, nor do suspended members or revoked and expired invitations.
Changing a viewer into any other role needs a free seat. Enforced **in the database** by a trigger on `organisation_member` and
`organisation_invitation`: adding past the limit is refused whatever the code path. Lowering the
limit below current use is allowed (the operator may do it at renewal) but blocks new invitations
until the company is back under it.

**Licence states** (`organisation.status`):

| State | Members can |
|---|---|
| `trial` | Everything, with **3 seats for 14 days** (`licence_ends_on` = start + 14 days) |
| `active` | Everything |
| `suspended` (lapsed, unpaid) | **Read only**: view and download, no changes. A banner says why |
| `closed` | Nothing. Data **kept 90 days** after closing (in case the company comes back or asks for an export), then deleted |

A daily job moves companies past `licence_ends_on` from `trial` or `active` to `suspended`, and
deletes companies `closed` for more than 90 days (`closed_at` records when).

## 4. Roles and permissions

Permissions are named actions the code checks (`member.invite`, `design.run`...); roles are sets of
them. The matrix lives in code (one place, tested exhaustively), not in the database: changing what a
role may do is a reviewed change.

| Permission | Owner | Admin | Builder | Electrician | Viewer |
|---|:-:|:-:|:-:|:-:|:-:|
| View projects, plans, designs, quotes | ✓ | ✓ | ✓ | ✓ | ✓ |
| Create / edit projects and clients | ✓ | ✓ | ✓ | | |
| Upload and edit floor plans, fixtures, briefs | ✓ | ✓ | ✓ | ✓ | |
| Run the engine; edit electrical designs | ✓ | ✓ | ✓ | ✓ | |
| Override a **mandatory** rule (with a recorded reason) | | | | ✓ ¹ | |
| Request a review | ✓ | ✓ | ✓ | ✓ | |
| **Approve / sign off a design** | ✓ ¹ | ✓ ¹ | | ✓ ¹ | |
| Create and send quotes | ✓ | ✓ | ✓ | | |
| Invite members, change roles, suspend, remove | ✓ | ✓ ² | | | |
| Edit company details | ✓ | ✓ | | | |
| See the licence and seat use | ✓ | ✓ | | | |
| Transfer ownership; close the company | ✓ | | | | |

¹ Only a person with a **verified electrical licence** in the project's state, whatever their role.
An owner who is a licensed electrician can sign off; a builder never can.
² An admin cannot change or remove an owner, nor make someone an owner.

**Rules that hold whatever the role:**
- A company always has at least one active owner (the last owner cannot leave, be demoted or removed).
- Nobody changes their own role.
- Reviews are by the company's own members only (already enforced by the schema).
- Builders cannot override mandatory rules (from the engine plan's v1 decisions).

## 5. How a request finds its company

1. The browser sends the Supabase token (as now) and the company it is working in:
   `X-Organisation-Id: <uuid>` (the company switcher sets it).
2. The API checks the person is an **active member** of that company, and the company is not
   `closed`; otherwise **403**. A person in one company only may omit the header.
3. In that request's transaction it sets `electriplan.organisation_id` and `electriplan.actor_id`
   (`SET LOCAL` — the one place plain SQL is necessary: Hibernate has no way to set a Postgres session
   setting), so row-level security limits every Hibernate query to that company.
4. The permission for the endpoint is checked against the member's role; writes on a `suspended`
   company are refused with **423 Locked** and a clear message.
5. The API connects to Postgres as an **ordinary role** (`NOSUPERUSER NOBYPASSRLS`), not the
   superuser it uses today, so row-level security actually applies. Flyway keeps the owner role.

## 6. Epic T — stories

Sizes: **S** ≤ 2 days, **M** 3–5 days, **L** 1–2 weeks.

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| T1 | ✅ **Done** — Seats and licence period | Migration V3 adds `seat_limit`, `licence_starts_on`, `licence_ends_on`, `closed_at`; trial defaults (3 seats, 14 days); a trigger refuses a seated member or invitation past the limit, never counts viewers, and checks a viewer promoted to another role; schema-rules tests for at, over and lowered limits, viewers, and promotion | S |
| T2 | ✅ **Done** — Runtime database role | A `NOSUPERUSER NOBYPASSRLS` role the API connects as, with the grants it needs; Flyway still migrates as the owner; local Docker, CI and README updated | M |
| T3 | ✅ **Done** — Company context per request | `X-Organisation-Id` resolution, membership and status checks (403), `SET LOCAL` per transaction; JPA entities for organisation and member; Postgres tests prove a query sees only the current company | L |
| T4 | Permission model | `Permission` enum and the role matrix in §4 in one place; an annotation on endpoints; a test for **every role × every permission**; the always-rules (last owner, own role) | M |
| T5 | Licensed sign-off | Record a member's licence; operator verifies it; sign-off requires a verified licence in the project's state | M |
| T6 | Company onboarding (operator) | Operator endpoint/tool: create a company with its seats, period and first owner; the owner gets the Supabase invitation email | M |
| T7 | Invitations | Invite by email with a role (seat checked), accept (joins the company), revoke, resend, expire after 7 days; uses Supabase's invite email | L |
| T8 | Member management | List members and seat use; change role; suspend and reinstate; remove; transfer ownership; every always-rule enforced | M |
| T9 | Licence states | Read-only when `suspended` (423 on writes), no access when `closed`; daily expiry job; banner in the UI | M |
| T10 | Team UI | Company switcher, Team page (members, roles, seat use "4 of 5"), invite dialog, role badges, read-only banner | L |
| T11 | Isolation over HTTP | Two companies, every endpoint, every role: nothing crosses, every refusal is the right status | M |
| T12 | Audit | Membership, role and licence changes written to `audit_event` | S |

**Order:** T1 → T2 → T3 → T4 → (T5, T6, T7, T8 in parallel) → T9 → T10 → T11 → T12.
**T1–T4 come before the electrical engine's first endpoint**: every engine endpoint (projects,
designs, reviews) needs to know which company the request is for and what the person may do.

## 7. Testing

- **Database:** seat trigger and isolation in `schema-rules.sql` (run as the ordinary role).
- **Permissions:** a generated test over every role × permission, from the matrix itself.
- **HTTP:** for every endpoint, unauthenticated (401), wrong company (403), right company wrong
  role (403), suspended company write (423), allowed (2xx).
- **Isolation:** two companies seeded side by side; every list and every id lookup checked for
  leakage.

## 8. Decided

Retention after closing (90 days), trial (3 seats, 14 days) and viewer seats (free) were settled on
2026-10-08 and are in §1.
