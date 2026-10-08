# Electrical Design Engine — Implementation Plan

FloorPlan JSON in → electrical design out: lights, switches, socket outlets,
wet-area restrictions, circuits, cable sizes, protection and a switchboard
schedule, laid out to Australian practice and checked against AS/NZS 3000.

```
FloorPlan JSON ─► enrich (room types, fixtures, brief) ─► wet-area zones
   ─► place points (lights, smoke alarms, fans, switches, GPOs, appliances)
   ─► load model ─► group into circuits ─► route cables ─► size cables & protection
   ─► switchboard schedule ─► validate every rule ─► drawings, schedules, BOM
```

Status: **plan, not yet started.** The engine is a **separate Java 21 / Spring
Boot service built with Spring Modulith** (§6): modules of the Electriplan Java API
(`backend/`). The floor-plan analyser (`floorplan/`, Python) stays as it is; the
engine is a new consumer of the `FloorPlan` it produces. Planna One is not involved in
v1; stock checks and real quotations against Planna are the next iteration (§6.7).

### v1 scope decisions

| Question | Decision | What it changes |
|---|---|---|
| First state | **Victoria** | The only state pack in v1 is `state/VIC.yaml`: Victorian Service & Installation Rules, the five Victorian distributors (CitiPower, Powercor, Jemena, United Energy, AusNet Services), Energy Safe Victoria requirements, and the Victorian Certificate of Electrical Safety (COES) as the sign-off the output prepares for. Other states come later as data, not code |
| Reviewers | **A panel of licensed electricians** | The reference designs (E0-S5) are drawn by two or more electricians independently, so the benchmark measures agreement with practice, not with one person's habits. At least one Victorian licensed electrician signs off each mandatory rule value (E16-S4) |
| Users | **Builders and electricians** | Two roles with different powers (E14-S7). Electricians can override a rule with a recorded reason. Builders cannot override mandatory rules; their edits to an electrician-reviewed design are flagged for re-review. Every output says the design must be reviewed and certified by a licensed electrician |
| Quoting | **Later, but design for it now** | v1 produces a complete, priced-ready BOM: item counts, device list, cable metres by type and size, each with a stable item code (E15-S3). Stock checks and real quotations come from Planna One ERP in the next iteration (E17, §6.7). The BOM format is fixed early so quoting can attach to it without rework |
| Platform | **Electriplan, a Spring Modulith service separate from Planna One** (Java 21, Spring Boot 3, Spring Modulith 1.4 — same line as Planna One) | The engine is modules of the Electriplan API, with Electriplan's own database and release cycle (§6). Vision stays in Python. Integrates with Planna One ERP in the next iteration for stock and quotations (§6.7) |
| Supply | **Single-phase only** | Brief `supply.phases` is fixed at 1. No phase balancing in v1 (moved to E17). If maximum demand exceeds what the distributor allows on a single phase, the engine raises a decision-required item recommending three-phase, rather than designing it |

### Expanding beyond Victoria

v1 designs Victorian houses, but nothing in the contracts, the model or the database is limited to
Victoria: the brief accepts every state, distributors are database rows, and AS/NZS 3000 (fixtures,
zones, circuits) is national. What differs by state is **rules** (smoke alarms in every bedroom in
Queensland, each state's service and installation rules) and **reference data** (its distributors).
Every design records which state's rules made it (`rulePack.state` in the design contract), so a
sign-off stays traceable after more states are added.

Adding a state, e.g. New South Wales:

| Step | What | Code change? |
|---|---|---|
| 1 | Encode its rules as `rulepacks/au-residential/state/NSW.yaml`: smoke alarms, service and installation rules, anything that overrides the national pack | No — data |
| 2 | A licensed electrician **from that state** signs the state rules off (recorded in the rule pack's `meta.yaml`, as for Victoria) | No |
| 3 | Add its distributors (Ausgrid, Endeavour Energy, Essential Energy) to `electricity_distributor`, with their supply limits | No — a migration that inserts rows |
| 4 | Reference plans from that state, designed by local electricians, to measure the engine against (§1) | No |
| 5 | Release the rule pack. The state is now supported | No |

**Rule for the engine (E0-S3 onwards): never hard-code "VIC only".** The states the engine designs
are the states that have a **signed-off state file in the current rule pack**; the readiness check
refuses any other state with a clear message ("Electriplan does not design NSW houses yet"). Adding
a state is then a rule-pack release, not an engine change.

---

## Contents

1. [What "90%" means](#1-what-90-means)
2. [Ground rules](#2-ground-rules)
3. [Standards and references](#3-standards-and-references)
4. [Inputs: what the FloorPlan gives us, and what it doesn't](#4-inputs)
5. [Output: the ElectricalDesign model](#5-output-the-electricaldesign-model)
6. [Architecture](#6-architecture)
7. [Rule catalogue](#7-rule-catalogue)
8. [Algorithms](#8-algorithms)
9. [Epics and stories](#9-epics-and-stories)
10. [Milestones](#10-milestones)
11. [Testing and validation](#11-testing-and-validation)
12. [Risks and open questions](#12-risks-and-open-questions)
13. [Glossary](#13-glossary)

---

## 1. What "90%" means

The engine produces a **design for review**, not a certified installation. In
every Australian state, electrical work, and the sign-off that it complies, is
done by a licensed electrician (the Certificate of Compliance / CES / COES,
depending on the state). The engine does the bulk of the drafting; the
electrician makes the judgement calls and signs.

"90%" is something we measure on a set of reference plans, not a slogan:

| Metric | Target | How it is measured |
|---|---|---|
| Points accepted unchanged | ≥ 90% | Electrician reviews an engine design; count lights, switches and GPOs kept as placed (moving one ≤ 300 mm counts as kept) |
| Circuits accepted unchanged | ≥ 90% | Same review, by circuit: same points, same protection |
| Cable sizes accepted | ≥ 95% | Same review |
| Mandatory-rule violations in output | 0 | The engine's own validator, plus the reviewer |
| Items flagged "needs electrician decision" | ≤ 10% | Count of design items carrying a `decision_required` flag |

The remaining 10% is expected and **reported, never hidden**: anything the
engine could not decide (unknown ceiling construction, an ambiguous fixture, a
DNSP-specific mains question) comes out as an explicit open item, never as a
silent guess.

## 2. Ground rules

1. **Deterministic.** Same FloorPlan + same brief + same rule pack → byte-for-byte
   the same design. No randomness without a fixed seed; no ML in the decision path
   for v1. A design must be reproducible when someone asks why it looks the way it does.
2. **Every decision is explained.** Each placed item and each sizing result
   records the rules that produced it and the values they used:
   `"GPO moved 450 mm left: AS/NZS 3000 cl. 6.2 zone 2 exclusion around shower_01"`.
3. **Rules are data, not code.** Clause numbers, distances, derating factors, lux
   targets and company preferences live in versioned rule packs (YAML), each
   value with a citation. Code implements *kinds* of rules (distance exclusion,
   count-per-area, table lookup), never the numbers.
4. **Three tiers of rule, kept apart:**
   - **Mandatory** — the Wiring Rules, NCC and state law. A violation is an error
     and blocks export.
   - **Regulatory-local** — state variations and DNSP service rules (smoke alarms
     in Queensland, consumer mains requirements per distributor). Chosen by the
     project's state and distributor.
   - **Design policy** — good practice and company preference (switch height,
     GPOs per bedroom, points per circuit, spare poles). A violation is a
     warning; the company can change these freely.
5. **The standards are licensed documents.** AS/NZS standards are copyright of
   Standards Australia. Rule packs record clause numbers and the *values* we
   apply, never transcribed text, and the business needs a current licensed copy
   of each standard listed in §3. **Every number in this document marked ⚠ is a
   working assumption for scaffolding and tests. It must be checked against the
   licensed standard, and signed off by a licensed electrician, before the rule
   pack is used on a real job.**
6. **Pure core, thin edges.** Inside each Spring Modulith module the design logic
   is plain Java — immutable records and pure functions, no I/O, no global state,
   no Spring. Controllers, repositories, events and the editor are adapters
   around it.
7. **Anchor to the plan.** Wall-mounted items are positioned like doors are now —
   `wallId` + distance along the wall + side + height — so they move with their
   wall when the user edits the plan. Ceiling items are positioned in plan
   coordinates inside a `roomId`.
8. **Never edit the FloorPlan.** The electrical design is a separate document
   that references the plan by id and records which plan revision it was made from.

## 3. Standards and references

Pin the edition of each in the rule pack's metadata. AS/NZS 3000 is under
revision; confirm the current edition and amendments before encoding.

| Reference | Governs | Used by epic |
|---|---|---|
| **AS/NZS 3000:2018** (Wiring Rules, with amendments) | Everything: RCDs, wet-area zones, socket outlet locations, wiring systems, max demand (Appendix C), voltage drop, earth fault loop impedance (Appendix B), switchboards, recessed luminaire clearances | All |
| **AS/NZS 3008.1.1** | Cable selection: current-carrying capacity, installation methods, derating (thermal insulation, grouping, ambient), voltage drop (mV/A·m) | E11 |
| **AS/NZS 3017** | Verification tests — what the electrician will test; drives what we must hand over (circuit schedule, expected Zs) | E12, E15 |
| **NCC 2022 Vol 2 / ABCB Housing Provisions** | Lighting power density (energy efficiency), smoke alarm locations, exhaust (bath, WC, laundry, kitchen) | E4, E5 |
| **AS 3786** | Smoke alarms (the device standard the NCC calls up) | E5 |
| **AS/NZS 60598.2.2 (Appendix ZZ)** | Recessed luminaire IC ratings (IC-4 can be covered by insulation) | E4 |
| **AS/NZS 1680 series** | Interior lighting levels; residential targets are taken as design policy | E4 |
| **AS 1428.1** | Accessible switch/GPO heights, where a project requires it | E6, E7 |
| **Victorian Service & Installation Rules** (v1); other states' SIRs later (NSW SIR, QLD QECM, WA WAER, SA SIR) | Metering, consumer mains, main switch, service protection device, single-phase demand limits | E8, E11, E12 |
| **Victorian legislation and ESV requirements** — Electricity Safety Act 1998, Electricity Safety (General) Regulations, Building Regulations 2018 | Licensing, COES, any Victorian variation on smoke alarms and installation practice | E5, E12, E15 |
| **Victorian distributor requirements** (CitiPower/Powercor, Jemena, United Energy, AusNet Services) | Supply limits per phase, Ze assumptions, mains and metering details | E8, E11 |
| **Other states' smoke alarm laws** (e.g. QLD Fire and Emergency Services Act) — later | State variations: in every bedroom, photoelectric, interconnected | E5 |
| **AS/NZS 4777, AS/NZS 5139, EV charger guidance** | Solar, batteries, EV — future epic | E17 |

## 4. Inputs

### 4.1 What the FloorPlan already gives us

From `backend/app/models.py` / `frontend/src/model/types.ts`, in millimetres:

| Field | Electrical use |
|---|---|
| `walls[]` (start, end, thickness) | Mounting surfaces for switches and GPOs; room boundaries; cable drops |
| `rooms[]` (name, polygon, labelPosition) | Room type → lighting level, GPO policy, wet-area check; ceiling layout area |
| `doors[]` (wallId, position, width, style, hingeAtStart, swing) | **Switch placement**: latch side and which side the leaf opens to are already modelled. Sliding/garage style changes the rule |
| `windows[]` | Wall space that cannot take a GPO or switch; curtain-pelmet clearance for downlights (policy) |
| `openings[]` | Unnamed gaps: treated as passages (no leaf), still need switching decisions |
| `labels[]`, `dimensions[]` | Not needed by the engine; dimensions confirm scale |
| `source.scaleConfidence` | Gate: below a threshold the engine refuses to size cables and asks for calibration |

### 4.2 What it does not give us — and must, before an electrical design is meaningful

| Missing | Why it matters | How we get it (E1) |
|---|---|---|
| **Room type** (not just a name) | `"ENS"`, `"BATH"`, `"WC"` → wet area; `"L'DRY"` → laundry rules; `"ALFRESCO"` → outdoor IP rating | Map `ROOM_NAME_DICTIONARY` names to a `RoomType` enum; user confirms in editor |
| **Fixtures**: shower, bath, basin, WC pan, kitchen sink, laundry tub, cooktop, oven, rangehood, island bench, kitchen bench run, HWS, AC unit, meter box | Wet-area zones are measured *from fixtures*, not rooms. Kitchen GPOs follow the bench. Dedicated circuits follow appliances | New `fixtures[]` in the plan model. v1: placed by the user in the editor from a palette. Later: detected from plan symbols |
| **Ceiling height** per room | Lumen method; zone heights; drop length for cable estimates | Brief default (2400 / 2550 / 2700 mm), per-room override |
| **Ceiling/roof construction** | Accessible roof space vs. second storey vs. raked ceiling — decides cable routing and downlight feasibility | Brief |
| **Thermal insulation in ceiling** | Cable derating; recessed luminaire clearances | Brief (default: yes — new builds are insulated) |
| **Switchboard and meter location** | Origin for every cable run; voltage drop | User places a `switchboard` fixture; default: garage wall nearest the street |
| **Supply**: phases, distributor, state, consumer mains route/length | Main switch, mains sizing, state rule pack | Brief |
| **Appliance schedule** | Induction vs gas cooktop, electric oven, HWS type (heat pump / storage / instantaneous gas), ducted vs split AC, EV, pool | Brief (checklist with sensible defaults) |
| **Storeys** | v1 is single-storey only | Brief; reject multi-storey until E17 |

**Readiness check.** Before running, the engine reports what is missing or
low-confidence and refuses to run the stages that depend on it — no wet-area
design without fixtures in wet rooms; no cable sizing without a switchboard
location and a trustworthy scale.

### 4.3 ProjectBrief (new, draft)

```jsonc
{
  "state": "VIC",                      // v1: VIC only. Selects the regulatory-local rule pack
  "distributor": "CitiPower",          // CitiPower | Powercor | Jemena | United Energy | AusNet Services
  "supply": { "phases": 1, "nominalVoltage": 230, "consumerMainsLengthM": 12 },   // v1: phases fixed at 1
  "construction": {
    "storeys": 1,
    "defaultCeilingHeight": 2550,
    "ceilingInsulated": true,
    "roofSpaceAccessible": true,
    "slab": true
  },
  "appliances": {
    "cooktop": "induction",            // induction | electric | gas | none
    "oven": "electric",
    "hotWater": "heat-pump",           // heat-pump | electric-storage | gas | solar-boosted
    "airConditioning": "ducted",       // ducted | split | none
    "evCharger": false,
    "pool": false
  },
  "preferences": {                     // overrides of design-policy defaults
    "downlightType": "IC4-10W-800lm",
    "switchHeight": 1100,
    "gpoHeight": 300,
    "spareSwitchboardPolesPct": 25
  }
}
```

## 5. Output: the ElectricalDesign model

A separate document, owned by the electrical engine. Its JSON Schema
(`contracts/electrical-design.schema.json`) is the source of
truth; Java records in the engine and generated TypeScript types in the editor
(`frontend/src/electrical/types.ts`) both follow it, camelCase on the wire (§6.6).

```jsonc
{
  "version": 1,
  "planRef": { "planHash": "sha256:…", "planVersion": 1 },
  "rulePack": { "id": "au-residential", "version": "2026.1", "standards": ["AS/NZS 3000:2018+A3"] },

  // Every electrical item. Placement is either on a wall or on a ceiling.
  "points": [
    { "id": "lt_001", "kind": "downlight", "roomId": "room_002",
      "placement": { "type": "ceiling", "at": { "x": 4200, "y": 1800 } },
      "spec": { "watts": 10, "lumens": 800, "ic": "IC-4", "ip": "IP20" },
      "circuitId": "c_L1", "controlledBy": ["sw_003"],
      "rationale": ["lumen-method: 4 × 800 lm for 150 lx in 14.2 m²", "grid 2×2, wall offset 950 mm"],
      "source": "engine" },

    { "id": "sw_003", "kind": "switch", "roomId": "room_002",
      "placement": { "type": "wall", "wallId": "wall_004", "position": 1460, "side": "left", "height": 1100 },
      "spec": { "gangs": 2, "ways": [ "1-way", "2-way" ] },
      "controls": [ ["lt_001", "lt_002", "lt_003", "lt_004"], ["lt_009"] ],
      "rationale": ["latch side of door_001, 100 mm from architrave", "2-way with sw_011 (second entry)"],
      "source": "engine" },

    { "id": "gpo_007", "kind": "gpo-double", "roomId": "room_005",
      "placement": { "type": "wall", "wallId": "wall_012", "position": 800, "side": "right", "height": 1150 },
      "spec": { "rating": 10, "ip": "IP33" }, "circuitId": "c_P2",
      "rationale": ["kitchen bench run, every 1200 mm (policy)", "≥ 150 mm from sink_01 (⚠ verify)"],
      "source": "engine" }
  ],

  // Wet-area and other keep-out volumes, for drawing and for validation.
  "zones": [
    { "id": "z_001", "kind": "bath-zone-1", "fixtureId": "shower_01",
      "polygon": [ … ], "floorToHeight": 2250, "rule": "AS/NZS 3000 cl. 6.2.2" }
  ],

  "circuits": [
    { "id": "c_L1", "type": "lighting", "label": "Lights — Beds & Hall",
      "points": ["lt_001", "lt_002"], "protectionId": "p_07",
      "demandA": 3.0, "cable": { "csa": 1.5, "type": "TPS 2C+E", "lengthM": 38.4 },
      "voltageDropPct": 1.9, "zsOhm": 2.1, "zsMaxOhm": 4.4,
      "route": [ { "from": "board", "to": "lt_001", "lengthM": 9.2 } ] }
  ],

  "switchboard": {
    "location": { "wallId": "wall_031", "position": 600 },
    "mainSwitch": { "ratingA": 63, "poles": 1 },
    "devices": [
      { "id": "p_07", "kind": "RCBO", "ratingA": 10, "curve": "C", "rcdMa": 30, "circuits": ["c_L1"] }
    ],
    "spd": { "fitted": true, "reason": "risk assessment — AS/NZS 3000 cl. 2.8 / Appendix F (⚠ verify)" },
    "polesUsed": 14, "polesTotal": 24
  },

  "maxDemand": { "method": "AS/NZS 3000 Appendix C", "perPhaseA": [48.6], "consumerMains": { "csa": 16, "type": "XLPE Cu" } },

  "violations": [
    { "ruleId": "wet.gpo.zone2", "severity": "error", "itemIds": ["gpo_011"], "message": "…" }
  ],
  "decisionsRequired": [
    { "id": "d_001", "itemIds": ["c_AC1"], "question": "Ducted AC unit rating unknown — assumed 7.1 kW / 20 A circuit" }
  ]
}
```

Item `source` is `engine` or `manual`; as in the plan, a user edit makes an item
`manual` and the engine never moves a manual item on a re-run — it designs around it.

## 6. Architecture

### 6.1 Shape of the system

The engine lives in the **Electriplan repository, as Spring Modulith modules of its
Java API** (`backend/`) — the service that already verifies Supabase sign-ins. It is
separate from the floor-plan analyser and separate from Planna One, with
Electriplan's own database, release cycle and rule-pack sign-off.

```
                    FloorPlan JSON                       ElectricalDesign JSON
 ┌──────────────┐  ───────────────►  ┌────────────────────────┐  ◄────────  ┌──────────────┐
 │ Floor-plan   │                    │  Electriplan API         │            │ Electriplan   │
 │ analyser     │                    │  electrical modules     │  ────────► │ web (React)  │
 │ Python ·     │                    │  Java 21 · Spring       │  design,   │ (electrical  │
 │ FastAPI      │                    │  Modulith · Postgres    │  validate  │  layer)      │
 └──────────────┘                    └───────────┬────────────┘            └──────────────┘
   image → FloorPlan                             │  future (E17): stock + pricing
   (unchanged)                                   ▼
                                     ┌────────────────────────┐
                                     │  Planna One ERP         │
                                     │  products, stock,       │
                                     │  price lists, quotes    │
                                     └────────────────────────┘
```

- **Floor-plan analyser** (`floorplan/`) stays in Python: OpenCV and Tesseract
  have no good Java equivalent. Its only output is FloorPlan JSON.
- **Electrical engine** takes FloorPlan + ProjectBrief and returns an
  ElectricalDesign. It owns projects, briefs, designs, reviews, rule packs and the
  item catalogue.
- **The editor** (`frontend/src/floorplan/`) gets an electrical layer that calls the
  engine at `/api/electrical/*`. That path already reaches the Java API (port 8081)
  through Vite's proxy and nginx; only `/api/floorplan/*` goes to the analyser.
- **Planna One** is not called in v1. In the next iteration the engine asks it for
  stock and prices of the items on the BOM and turns the BOM into a real quotation
  (§6.7, E17).

### 6.2 Technology

Matched to Planna One's backend so the two share skills, tooling and deployment.

| Concern | Choice | Why |
|---|---|---|
| Language / runtime | **Java 21** | Records, sealed interfaces and pattern matching suit an immutable design model |
| Framework | **Spring Boot 3.x + Spring Modulith 1.4** (same line as Planna One) | Enforced module boundaries, module events, generated module docs |
| Build | Maven | As Planna One |
| Geometry | **JTS Topology Suite** | Polygons, buffers, offsets, clipping, point-in-polygon — the library shapely itself descends from |
| Optimisation | Greedy first; **Timefold Solver** as the escalation path for circuit grouping | Built for assignment problems with hard/soft constraints (§8.6) |
| Rule packs | YAML via Jackson, bound to records, Bean Validation | Rules stay data (§2.3) |
| Persistence | PostgreSQL + Flyway, Spring Data JDBC | Projects, briefs, designs (JSONB), reviews, company policy overrides |
| API | REST + springdoc-openapi | OpenAPI published for the editor and, later, Planna |
| Auth | Supabase JWT verification, as in Planna One | One identity across products; roles builder / electrician (E14-S7) |
| Output | Apache PDFBox (PDF), generated SVG (drawings, single-line diagram), DXF writer (R12 ASCII is simple enough to write directly) | E15 |
| Tests | JUnit 5, AssertJ, **jqwik** (property tests), Testcontainers (Postgres), Spring Modulith `ApplicationModules.verify()` + `@ApplicationModuleTest` | §11 |

### 6.3 Modules

Modules are direct sub-packages of the API's application package,
`com.hypex.electriplan` (renamed from the scaffold's `loginpage`). Each top-level package is a
Spring Modulith application module, beside the existing `security` and `users`; other modules may only use what it exposes in its root
package (or a named interface), and `ApplicationModules.of(…).verify()` runs in CI.

```
contracts/                       JSON Schemas: floor-plan, electrical-design, project-brief, fixture (§6.6)
backend/
  pom.xml
  src/main/java/com/hypex/electriplan/
    security/      (exists) Supabase JWT verification
    users/         (exists) Copy of Supabase's users
    plan/          FloorPlan intake & validation, fixtures, room types, readiness check   (E1)
    rules/         Rule-pack loading, tiers & precedence, company overrides, standards register (E0)
    catalogue/     Item kinds and specs (luminaires, GPOs, devices, cables) with stable item codes (E4, E15)
    geometry/      Electrical geometry kernel on JTS — shared module (OPEN)                (E2)
    zones/         Wet-area zones and the permission matrix                                 (E3)
    layout/        Points: lighting, ceiling devices, switches, outlets (sub-packages)      (E4–E7)
    loads/         Point loads, maximum demand (Appendix C), single-phase check             (E8)
    circuits/      Grouping, RCD/RCBO assignment, naming                                    (E9)
    cabling/       Routing, lengths, cable & protection sizing, VD, Zs                       (E10–E11)
    switchboard/   Board schedule, single-line diagram                                       (E12)
    validation/    Runs every rule against any design; compliance report                     (E13)
    design/        Orchestrator, design REST API, design persistence, events                 (E0, E14)
    review/        Roles, electrician review, acceptance metrics                              (E14-S7, E16)
    output/        PDF set, schedules, BOM, DXF                                               (E15)
    quoting/       (next iteration) Planna One client, stock & price lookup, quotations       (E17)
  src/main/resources/
    rulepacks/au-residential/
      meta.yaml                  Editions pinned, sign-off record
      wet-areas.yaml  lighting.yaml  switches.yaml  outlets.yaml  smoke.yaml
      demand.yaml                AS/NZS 3000 Appendix C tables
      cables.yaml                AS/NZS 3008.1.1 tables (CCC, derating, mV/A·m)
      protection.yaml  switchboard.yaml
      state/VIC.yaml             v1; other states later as further files
      company/default.yaml       Neutral design-policy defaults
    db/migration/                Flyway
  src/test/java/…                Per-module tests; reference plans under src/test/resources/plans/
```

National, state and neutral-default rule packs ship **inside the service**, so a
rule change is a reviewed, versioned release. Company overrides
(`company/<company>.yaml` in §E0-S7) are stored **in the database**, because
companies edit them, and are validated against the same schema on save.

**Pure core, Spring at the edges.** Inside each module, the design logic is plain
Java — records in, records out, no Spring, no I/O — so it is unit-tested without an
application context. Spring lives in each module's adapters: REST controllers,
repositories, event listeners and configuration.

### 6.4 Stage contract

Every stage has the same shape, so stages can be tested, replaced and re-run alone:

```java
public interface DesignStage {
    StageResult run(FloorPlan plan, ProjectBrief brief,
                    ElectricalDesign design, RulePack rules);
}

public record StageResult(ElectricalDesign design,          // design with this stage's additions
                          List<Rationale> rationale,
                          List<Decision> decisionsRequired,
                          StageReport report) {}
```

Each layout/loads/circuits/cabling/switchboard module contributes its stages; the
`design` module's orchestrator runs them in a fixed order. Stages only **add** to
the design; they never remove manual items. The orchestrator records a
`StageReport` per stage — the same honest-reporting pattern as `AnalysisStep` in
the vision pipeline.

The run is synchronous: a typical house should design in well under a few seconds,
so there is no need for queues in v1. On completion the `design` module publishes
a `DesignCompleted` event (Spring Modulith event publication registry, so it is not
lost on a crash); `output` listens to build the BOM, and `quoting` will listen to
it in the next iteration without the engine changing.

### 6.5 API (v1)

| Endpoint | Does |
|---|---|
| `POST /api/electrical/projects` | Create a project from a FloorPlan + brief |
| `PUT /api/electrical/projects/{id}/plan` | Replace the plan (after re-editing walls/fixtures); marks the design stale |
| `GET /api/electrical/projects/{id}/readiness` | Readiness check (§4.2) |
| `POST /api/electrical/projects/{id}/designs` | Run the engine; keeps manual items |
| `GET /api/electrical/designs/{id}` | The ElectricalDesign |
| `PUT /api/electrical/designs/{id}` | Save user edits; re-validates |
| `POST /api/electrical/designs/validate` | Validate any design without saving (live editing) |
| `POST /api/electrical/designs/{id}/review` | Electrician review / sign-off (role: electrician) |
| `GET /api/electrical/designs/{id}/bom` | Quote-ready BOM (§E15-S3) |
| `GET /api/electrical/designs/{id}/export?format=pdf\|dxf\|json` | Outputs |

### 6.6 Contracts across three languages

FloorPlan is defined in Python and TypeScript today; Java makes three. To stop them
drifting:

- **JSON Schema is the source of truth.** `floor-plan.schema.json` lives with its
  owner; all schemas sit together in `contracts/` at the repository root, next to
  the three codebases that use them (`floorplan/`, `frontend/`, `backend/`).
- **TypeScript** types are generated (json-schema-to-typescript) and **Python**
  models generated or checked (datamodel-code-generator) in CI.
- **Java** types are hand-written records — so units and anchors can be real types
  (`Millimetres`, `WallAnchor`, `CeilingPoint`) rather than bare doubles — and a
  **contract test** serialises every reference design and validates it against the
  schema, and deserialises every schema example. A schema change that the Java
  model does not follow fails the build.
- Schemas carry a `version`; the engine accepts the FloorPlan versions it lists and
  rejects the rest with a clear error.

### 6.7 Planna One integration (next iteration)

Not built in v1, but v1 is shaped so it slots in:

- The `catalogue` module gives every BOM line a **stable item code** and a full
  spec (e.g. `DL-IC4-10W-800LM-TRI`, `GPO-DBL-10A-WHT`, `CBL-TPS-2.5-2CE`). The
  next iteration adds a mapping from item codes to Planna product SKUs (many codes
  may map to one SKU; one code may have preferred and alternative SKUs).
- The `quoting` module will be the only module that knows Planna exists. It calls
  Planna's API (authenticated with a service token, not a user's) for **stock on
  hand** per SKU and **price lists** for the customer, then produces a quotation:
  materials from the BOM, labour from per-item rates, margin, and lines flagged
  where stock is short or an alternative SKU was used.
- Planna is behind a port interface (`ProductCatalogue`, `StockLookup`,
  `PriceLookup`), with a fake implementation for tests and for running the engine
  without Planna.
- Failures are tolerated: if Planna is unavailable the design and BOM still work;
  only the quotation waits. Calls are cached per quote and time-outs are short.
- Whether the engine pushes a draft quote into Planna, or Planna pulls the BOM, is
  decided in that iteration; both are possible because the BOM is a published,
  versioned contract.

### 6.8 Placement vs. validation — two engines, one rule set

- **Generators** (stages) use rules *constructively*: "where may a GPO go?"
- **The validator** uses the same rules *as checks*: "is this GPO allowed?"

The validator runs on everything, including designs the user has edited by hand.
A generator must never produce something the validator rejects — a test asserts
this over the whole reference set (§11).

### 6.9 Rule representation

Each rule is one entry in a pack, with a kind the code knows how to evaluate:

```yaml
- id: wet.gpo.zone2
  tier: mandatory
  kind: exclusion            # item kinds may not be placed inside a zone kind
  applies_to: [gpo-single, gpo-double, switch]
  zone: bath-zone-2
  exceptions: [ shaver-outlet-isolating-transformer ]   # ⚠ verify list
  cite: "AS/NZS 3000:2018 cl. 6.2.4"                   # ⚠ verify clause
  message: "Socket outlets are not permitted in zone 2 of a bath or shower"

- id: policy.switch.height
  tier: policy
  kind: value
  value_mm: 1100
  cite: "Company standard; AS 1428.1 range 900–1100 where accessible"
```

Rule kinds needed for v1: `exclusion` (item in zone), `clearance` (item ≥ d from
fixture/item/corner), `count_per` (≥ n per room/area/length), `value` (a parameter),
`table` (lookup, with interpolation rules), `limit` (computed value ≤ limit),
`requires` (if A then B: "wet room ⇒ exhaust fan if no openable window").

## 7. Rule catalogue

What the engine has to know, grouped by area. This is the backlog for the rule
packs; each line becomes one or more rule entries with a citation.
**All numeric values marked ⚠ are working assumptions — verify against the
licensed standard before encoding.**

### 7.1 Wet areas (AS/NZS 3000 Section 6)

- Zones around **baths and showers**, measured from the fixture:
  - Zone 0: inside the bath or shower base.
  - Zone 1: above zone 0 to 2.25 m from the floor; for a shower without a
    fixed enclosure, out to 1.2 m horizontally from the fixed water outlet. ⚠
  - Zone 2: 0.6 m horizontally beyond zone 1, to 2.25 m high. ⚠
  - Zone 3: 2.4 m horizontally beyond zone 2, to 2.25 m high. ⚠
  - Fixed screens and walls reduce zones (the "shadow" rule): a zone does not pass
    through a fixed, floor-to-ceiling barrier. ⚠ Needs geometry: zone polygons are
    clipped by walls and by screen fixtures.
- What may go in each zone — socket outlets, switches, luminaires, fans, heaters —
  with the required IP rating and whether SELV / RCD / a specific device class is
  needed. Encode as a matrix: `item kind × zone → allowed | allowed-if(ip, rcd, selv) | forbidden`.
- **Basins, sinks and laundry tubs**: restricted zone around the tap/basin for
  socket outlets and switches (distance ⚠ verify, commonly cited as 150 mm from
  the edge for sinks/tubs; a separate basin rule applies).
- **Swimming pools and spas, saunas** (Section 6.3–6.5): zones from the water's
  edge. Out of scope for v1; the model must still allow a `pool` fixture so the
  engine can say "pool present — not designed".

### 7.2 RCD protection and circuit separation (AS/NZS 3000 cl. 2.6)

- Every final subcircuit supplying socket outlets, lighting, or directly connected
  hand-held equipment in a domestic installation: **30 mA RCD**.
- Where there is more than one such circuit, they are spread over **at least two
  RCDs**, and **no more than three final subcircuits per RCD**. ⚠ verify exact wording
- Lighting circuits spread so that one RCD tripping does not black out the whole
  dwelling (policy reinforcing the above).
- RCBO per circuit satisfies both and is the default policy for new boards.
- Smoke alarm circuit treatment: on a lighting circuit or dedicated, per state
  rule and policy. ⚠

### 7.3 Lighting

- **NCC lighting power density** (Housing Provisions, energy efficiency): ⚠ verify
  - Interior of the dwelling: ≤ 5 W/m²
  - Verandahs, balconies, outdoor: ≤ 4 W/m²
  - Class 10a (garage, shed): ≤ 3 W/m²
  - Adjustment factors for controls (dimmers, motion sensors) — model them.
  - Checked over the whole dwelling as an allowance, not per room.
- **Recessed luminaires and insulation** (AS/NZS 3000 cl. 4.5): default to IC-4
  rated downlights, which can be abutted and covered by insulation. Non-IC fittings
  need the clearances in the clause and a "insulation gap" note on the drawing. ⚠
- **Lighting levels** — design policy, taken from AS/NZS 1680 guidance. Starting
  targets: ⚠ policy, tune with electricians
  | Room type | Target (lx, avg) |
  |---|---|
  | Living, family, bed | 100–150 |
  | Kitchen | 240–300 (task over bench) |
  | Bath, ens, laundry | 150–240 |
  | Hall, WIR, WC | 80–100 |
  | Garage | 100 |
- **Spacing**: max spacing-to-mounting-height ratio from the luminaire's photometric
  data (typ. ≤ 1.5 for downlights); wall offset ≈ half spacing; minimum distance from
  walls (e.g. 300 mm) and from smoke alarms. Policy.
- Exterior: entry light at every external door; alfresco/porch lighting; garage.
- Pendants over island benches and dining (policy, optional).

### 7.4 Switches

No height or position is mandated for general residential switches; this is
mostly **policy** (with AS 1428.1 where accessibility applies):

- Height to centre: 1100 mm AFL (policy; range 900–1200).
- On the **latch side** of the entry door, inside the room, 50–150 mm from the
  architrave — never behind the door leaf. `door.hingeAtStart` and `door.swing`
  give us this directly.
- Rooms with two or more entries more than ~4 m apart (policy) and every hallway
  with switches at both ends: **2-way** switching; three or more control points:
  intermediate switches.
- Wet rooms: switch outside the room, or inside but outside the zones it is
  forbidden in (§7.1).
- Ganging: loads in one room controlled from the same spot share one plate
  (lights, exhaust fan, ceiling fan controller).
- Exterior lights: switch inside, next to the door they serve.
- Garage: switch at the internal entry door; also at the roller door (policy).

### 7.5 Socket outlets (GPOs) and dedicated outlets

- Mandatory: wet-area exclusions (§7.1); outdoor outlets weatherproof (IP rating);
  RCD protection (§7.2); none in positions forbidden near cooktops/sinks. ⚠
- Policy — starting point, tuned with electricians:
  | Room type | Default |
  |---|---|
  | Bedroom | 2 × double; beside both sides of the likely bed wall |
  | Master | 3 × double |
  | Living / family | 1 double per wall ≥ 2.4 m of free length, ≥ 3 total; TV wall gets one at ~1200 mm and data |
  | Kitchen | Bench: one double every 1200 mm of bench run, ≥ 150 mm from sink and away from the cooktop ⚠; plus fridge, microwave, dishwasher, rangehood |
  | Laundry | Washer, dryer, one bench double |
  | Bath / ens | One double outside zones, for a hair dryer/shaver (policy, if space) |
  | Garage | 2 doubles, plus one at ceiling for the door motor |
  | Hall | One double per 5 m |
  | Outdoor | One weatherproof double per alfresco/patio |
- Avoid: behind doors, within 300 mm of a corner (policy), under windows only if
  no other wall space exists.
- Heights: 300 mm AFL general; 1100 mm / 150 mm above bench in kitchen and laundry.

### 7.6 Ceiling devices

- **Smoke alarms** (NCC + AS 3786 + state laws): mains-powered with battery
  backup, interconnected; at least: between bedrooms and the rest of the dwelling,
  in the hallway serving bedrooms, each storey. v1 applies the NCC requirement
  plus any Victorian variation (confirm against the Building Regulations 2018 and
  ESV guidance ⚠); policy may go further (e.g. one in every bedroom), as a warning
  tier rule. Later state packs override — e.g. **QLD: in every bedroom and
  hallway, photoelectric, interconnected.** Placement:
  on the ceiling, ≥ 300 mm from walls/corners ⚠, away from exhaust fans, AC
  outlets and kitchens (nuisance alarms).
- **Exhaust fans** (NCC): in bathrooms, WCs and laundries without adequate natural
  ventilation, and in kitchens (rangehood), ducted outdoors. Flow rates are an NCC
  matter ⚠. Bath fan near the shower, outside zone 1 unless rated for it.
- Ceiling fans (optional, policy): bedrooms and living; centred in the room; needs
  a clear radius from downlights and smoke alarms.
- Bathroom heat/fan/light combos: same zone rules.

### 7.7 Loads and maximum demand (AS/NZS 3000 Appendix C)

- Load groups and the demand each contributes: lighting (by point count), socket
  outlets (by point count, 10 A vs 15 A), ranges/cooktops/ovens (fraction of
  nameplate), water heaters, air conditioning, EV, pool, etc. Encode the Appendix C
  domestic tables exactly. ⚠ (Commonly quoted: lighting 3 A for the first 20
  points + 2 A per additional 20; 10 A outlets 10 A for 1–20 points + 5 A per
  additional 20 — illustrative only.)
- v1 is single-phase: one demand figure. Three-phase and balancing are E17.
- Results drive the main switch and consumer mains size. If demand exceeds the
  Victorian distributor's single-phase limit (VIC SIR / distributor rules ⚠), the
  engine stops short of mains sizing and raises a decision-required item
  recommending a three-phase supply.

### 7.8 Cables, voltage drop and fault loop (AS/NZS 3000 + AS/NZS 3008.1.1)

- **Current-carrying capacity** per cable type and installation method (e.g. TPS
  unenclosed in roof space, enclosed in conduit, buried), with derating for
  thermal insulation (partially / completely surrounded), grouping and ambient
  temperature. Tables from AS/NZS 3008.1.1.
- **Voltage drop**: ≤ 5% from the point of supply to any point (AS/NZS 3000
  cl. 3.6). Split a budget between consumer mains and final subcircuits (policy,
  e.g. mains ≤ 1–2%, subcircuits the rest).
- **Earth fault loop impedance / disconnection time**: 0.4 s for final
  subcircuits supplying socket outlets and hand-held equipment, 5 s for others
  (at 230 V); max Zs per device type/curve/rating from Appendix B. ⚠ This also
  caps cable length.
- **Protection coordination**: Ib ≤ In ≤ Iz (design current ≤ device rating ≤
  cable capacity).
- Typical results the engine should reproduce on ordinary homes (sanity checks,
  not rules): lighting 1.5 mm² on 10 A; GPOs 2.5 mm² on 16–20 A; oven 2.5–4 mm²
  on 20–25 A; cooktop / induction 6 mm² on 32 A; HWS 2.5 mm² on 20 A.
- **Wiring zones**: cables concealed in walls less than 50 mm from the surface run
  vertically or horizontally from an accessory, or within the defined zones near
  ceilings/corners, unless mechanically protected. ⚠ The routing model drops
  vertically from the ceiling above each wall item, which satisfies this by construction.

### 7.9 Switchboard

- Main switch rated for maximum demand; per state SIR.
- RCD/RCBO arrangement per §7.2.
- Surge protection: risk assessment per AS/NZS 3000 (⚠ verify clause); default
  policy: fit an SPD.
- MEN link, earth, neutral bars — drawn on the single-line diagram.
- Spare capacity: policy (default 25% spare poles), and space for solar/battery/EV.
- Circuit schedule and labelling: every circuit identified (required).
- Location: accessible, not in a wet area, not in a cupboard over a bench etc.
  (state and AS/NZS 3000 rules ⚠).

## 8. Algorithms

### 8.1 Electrical geometry kernel (E2)

The plan stores walls as centrelines with a thickness. Electrical items mount on
**wall faces**, so the kernel first derives, for each room:

1. **Room faces** — for each wall, the face (left/right of the centreline, offset
   by thickness/2) that bounds the room, clipped to the room polygon.
2. **Free intervals** along each face — the face length minus door openings
   (plus leaf swing zones), windows, corners (policy clearance) and fixture
   footprints. Every wall-mounted generator places items only inside free intervals.
3. **Ceiling region** — the room polygon offset inwards by the wall clearance.
4. **Zones** — 2D polygons with a height range, built from fixtures and clipped
   by walls (§8.2).

Queries: `point_in_zone`, `distance_to_fixture`, `nearest_free_position(face, target)`,
`face_for(door, side)`. Everything is in mm, on JTS geometry, following the same
conventions as the vision pipeline's `geometry.py` and the editor's
`geometry/wall.ts` (wall direction, left/right normal, position along wall).

### 8.2 Wet-area zones (E3)

For each bath/shower fixture: build zone 0 from its footprint, buffer outwards by
the zone radii (from the outlet point for unenclosed showers), cap at 2.25 m,
then clip by walls and fixed screens (a zone may not pass through a full-height
barrier; around a partial screen it wraps — model as visibility-polygon clipping
from the fixture). Basins/sinks/tubs get their own restricted buffers. Output:
`zones[]` with height ranges, and a query API for every later stage.

### 8.3 Downlight layout (E4)

1. Target illuminance `E` from room type; luminaire `Φ` from the catalogue;
   utilisation factor `UF` from room index (`k = L·W / (h·(L+W))`) and reflectances
   (policy defaults); maintenance factor `MF` (policy, ~0.8).
2. Count: `N = ceil(E · A / (Φ · UF · MF))`.
3. **Rectangular (or near-rectangular) rooms**: choose rows × columns closest to
   the room's aspect ratio such that spacing ≤ max S/H ratio; place on a centred
   grid with wall offset ≈ s/2. Symmetry is what electricians and clients expect —
   prefer a symmetrical grid over the exact count when the difference is within one fitting.
4. **L-shaped and irregular rooms**: decompose into rectangles (largest-rectangle
   decomposition of the orthogonal polygon), lay out each, then merge points closer
   than a minimum separation. Fallback for genuinely irregular shapes: Lloyd
   relaxation (centroidal Voronoi) inside the ceiling region, seeded from a grid,
   fixed iterations → deterministic.
5. Remove/shift points that collide with smoke alarms, exhaust fans, ceiling fans,
   zones forbidding them, and bulkheads.
6. Check the dwelling against the NCC W/m² allowance; if over, reduce counts in
   the lowest-priority rooms first and record why.

### 8.4 Switch placement (E6)

1. For each room, list its **entries**: doors, openings, sliding doors, and the
   rooms they connect.
2. For each entry, pick the mounting point: inside face, latch side
   (`hingeAtStart` → latch is at the other jamb), offset `d` from the jamb, at switch
   height; if that face is not free (window, corner, other door), walk along the
   face to the nearest free spot and record it.
3. Decide control points: one entry → 1-way; two+ entries farther apart than the
   policy distance → 2-way between them; hallway ends → 2-way; three+ → intermediate.
4. Gang loads per location (lights, fan, exterior light at that door).
5. Wet rooms: if the chosen point is in a forbidden zone, move outside the room
   to the corridor face of the same door.
6. Openings without a door: switch on the side of the room being entered, on the
   nearer face.

### 8.5 GPO placement (E7)

Per room, from the policy table: compute required count, then place on free
intervals by scoring candidate positions (every 100 mm along free faces):
`score = coverage(spread around the room) + furniture-likelihood (bed wall, TV wall)
− penalties (near door swing, under window, near corner)`, subject to hard rules
(zones, clearances). Greedy with deterministic tie-breaking. Kitchens are driven by
the bench fixture: walk the bench run, place every N mm, skip sink/cooktop exclusions.
Appliance outlets go where their fixture is (fridge space, dishwasher, washer).

### 8.6 Circuit grouping (E9)

1. **Dedicated circuits** first, from appliances: cooktop, oven, HWS, AC, dryer,
   dishwasher (policy), EV, etc.
2. **General circuits** per type (lighting, GPO), grouped by spatial clustering:
   seed from the room farthest from the board, grow through adjacent rooms
   (room adjacency graph from shared walls/doors) until a limit is hit:
   points per circuit (policy, e.g. ≤ 20 lights / ≤ 10 doubles), demand ≤ device
   rating, estimated voltage drop ≤ budget.
3. Kitchen/laundry GPOs on their own circuits (policy).
4. Assign circuits to RCDs (or RCBOs): respect ≤ 3 per RCD and the lighting split.
   (Phase balancing is not needed for v1's single-phase supply; E17.)
5. Escalation path if greedy results are poor on the reference set: model it in
   **Timefold Solver** — points as planning entities assigned to circuits; hard
   constraints for device rating, points-per-circuit and voltage drop; soft
   constraints to minimise cable length and keep circuits spatially compact. Run
   with a fixed seed and step limit so the result stays deterministic (§2.1).

### 8.7 Cable routing and length (E10)

v1 assumption: single storey with an accessible roof space (brief says otherwise
→ decision required).

- Ceiling items connect in the roof space in straight lines (cables cross over
  walls freely in the roof space).
- Wall items: vertical drop from the ceiling directly above, length =
  ceiling height − item height + top plate allowance.
- Per circuit: order points as a daisy chain from the board using a
  nearest-neighbour tour + 2-opt (deterministic), or a minimum spanning tree when
  branching is allowed (lighting loops via switch). Length = horizontal run + drops
  + termination allowance + a policy waste factor (e.g. +10%).
- Switch loops for lighting: cable from the light group to the switch drop.
- Output per circuit: path polylines (for drawing) and total length (for sizing,
  BOM and quote).

### 8.8 Cable and protection sizing (E11)

For each circuit:
1. Design current `Ib` from the load model (§7.7) and diversity.
2. Pick protection `In ≥ Ib` (standard ratings; curve C default, policy).
3. Pick the smallest cable with derated `Iz ≥ In` for its installation method
   (in insulation → derated).
4. Check voltage drop with the route length (mV/A·m tables); upsize until within
   the subcircuit budget.
5. Check Zs = Ze + (R1 + R2) for disconnection time; upsize or reduce length if
   it fails. `Ze` from the brief (default per Victorian distributor ⚠).
6. Record every step in the rationale so the reviewer can follow the arithmetic.

## 9. Epics and stories

Bottom-up: each epic depends only on those above it. Sizes are rough —
**S** ≤ 2 days, **M** 3–5 days, **L** 1–2 weeks. Every story ends with tests and
a rule-pack entry where it touches a rule.

### E0 — Foundations

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E0-S1 | Add the electrical modules to the Electriplan API | ~~Rename the application package~~ (done: `com.hypex.electriplan`); empty modules from §6.3 beside `security` and `users`; `ApplicationModules.verify()` test passes; health endpoint; Postgres + Flyway via Testcontainers; CI build; `DesignStage` contract and an orchestrator that runs an empty stage list and returns an empty valid design | M |
| E0-S2 | ✅ **Done** — `ElectricalDesign`, `ProjectBrief`, `Fixture` JSON Schemas + Java records ([contracts/](../contracts/README.md)) | Schemas in `contracts/`; Java records with unit types; contract tests validate serialised output against the schemas; TS types generated for the editor | M |
| E0-S2a | FloorPlan schema as the shared contract | `contracts/floor-plan.schema.json`; Python and TS checked against it in CI; engine reads it into Java records and rejects unsupported versions | M |
| E0-S3 | Rule-pack format, loader and schema validation | Loads YAML packs; rejects a rule without `id`, `tier`, `kind`, `cite`; tier precedence (state overrides national, company overrides policy only — never mandatory) tested; **supported states = states with a signed-off state file** (no hard-coded VIC; see *Expanding beyond Victoria*) | M |
| E0-S4 | Rationale & decision-required plumbing | Any stage can attach rationale lines and decisions; they appear in the output and in the `validation` module's compliance report | S |
| E0-S5 | Reference plan set | 10 typical Victorian single-storey, single-phase plans as FloorPlan + brief + fixtures: studio, 2-bed unit, 3-bed, 4-bed with ensuite, L-shaped living, open-plan kitchen with island, two bathrooms back-to-back, large garage, all-electric home (induction + heat pump, tests the single-phase demand limit), one plan per Victorian distributor across the set. Each designed independently by ≥ 2 electricians from the panel. Stored under `backend/src/test/resources/plans/` | M |
| E0-S6 | Standards register | `meta.yaml` pins editions; a script lists every rule with its citation and ⚠ status; CI fails if a mandatory rule is unverified in a "release" pack | S |
| E0-S7 | Neutral default policy + company overrides | `company/default.yaml` holds neutral, common-practice values agreed by the panel; `company/<company>.yaml` overrides policy-tier values only; loader rejects a company file that touches a mandatory or state rule | S |
| E0-S8 | Obtain the standards (business task, not code) | Licensed access to AS/NZS 3000:2018 (+ amendments) and AS/NZS 3008.1.1 — from the Standards Australia store or a subscription service (a multi-user subscription is better than single PDFs, so the panel and developers can all refer to it). The Victorian Service & Installation Rules are published by the Victorian distributors and free to download. NCC is free from ABCB. Licence holder and edition recorded in `meta.yaml`. **Blocks any "release" rule pack, not development** | S |

### E1 — Plan enrichment and project brief

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E1-S1 | `RoomType` enum + mapping from room names | Every name in `ROOM_NAME_DICTIONARY` maps to a type; unknown → `unknown` with a decision-required item | S |
| E1-S2 | `fixtures[]` in the FloorPlan schema (and so in the Python, TS and Java models) | Shower, bath, basin, WC, sink, tub, cooktop, oven, rangehood, bench run, island, fridge space, dishwasher, washer, dryer, HWS, AC unit, meter box, switchboard, pool; position, rotation, footprint, optional `wallId` anchor | M |
| E1-S3 | Fixture palette and placement in the editor | Place, move, rotate, delete fixtures; snaps to walls; saved in plan JSON; follows the five-step pattern in README §8 | L |
| E1-S4 | Per-room ceiling height and type (flat / raked / bulkhead) | Editable in properties panel; defaults from brief | S |
| E1-S5 | ProjectBrief form | Captures every field in §4.3 with defaults; validated server-side | M |
| E1-S6 | Readiness check | Lists missing data per stage (e.g. "BATH has no shower or bath fixture — wet-area design skipped"); shown before running | M |
| E1-S7 | (Later) Fixture detection from plan symbols | Detect baths, showers, basins, WCs, sinks from drawing symbols with confidence; user confirms. Reuses the vision pipeline | L |

### E2 — Electrical geometry kernel

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E2-S1 | Room faces from walls + room polygons | For each room, the wall faces bounding it, with side and clipped extent; tested on L-shaped and T-junction rooms | M |
| E2-S2 | Free intervals on faces | Subtract doors (+ swing clearance), windows, corners, fixture footprints; tested | M |
| E2-S3 | Wall-anchored ⇄ plan-coordinate conversion | `(wallId, position, side)` ⇄ `(x, y)`; inverse is stable; mirrors frontend `geometry/wall.ts` | S |
| E2-S4 | Ceiling region and orthogonal decomposition | Inward offset; rectangle decomposition of orthogonal polygons; tested on L, U, T shapes | M |
| E2-S5 | Room adjacency graph | Rooms connected by doors/openings and by shared walls; used for switching and circuit grouping | S |
| E2-S6 | Zone primitives | Buffer, clip by walls (visibility), height ranges, `point_in_zone` | M |

### E3 — Wet-area zone engine

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E3-S1 | Bath/shower zones 0–3 from fixtures | Zones generated per §7.1/§8.2; golden-image tests on the reference bathrooms | M |
| E3-S2 | Barrier clipping (walls, fixed screens) | A zone does not pass through a full-height wall/screen; wraps around partial screens | M |
| E3-S3 | Basin / sink / tub restricted areas | Per rule pack | S |
| E3-S4 | Permission matrix | `allowed(item_kind, spec, zone)` with IP/RCD/SELV conditions from the pack; exhaustive table tests | M |
| E3-S5 | Zones drawn in editor | Toggleable layer, hatched by zone number | S |

### E4 — Lighting layout

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E4-S1 | Luminaire catalogue | Downlight, oyster, batten, pendant, exterior wall light, IP-rated wet-area fitting: W, lm, IC, IP, beam | S |
| E4-S2 | Lumen-method count per room | Count from §8.3; unit tests with hand-worked examples | S |
| E4-S3 | Grid layout for rectangular rooms | Centred symmetrical grid within S/H limit; property test: all points inside ceiling region | M |
| E4-S4 | Irregular rooms | Decomposition + merge; Lloyd fallback; deterministic | M |
| E4-S5 | Exterior and garage lighting | Light at every external door; alfresco grid; garage battens/downlights | S |
| E4-S6 | NCC lighting power density check | Whole-dwelling allowance by area type; reduction strategy when over; violation if still over | M |
| E4-S7 | Wet-area fitting selection | In-zone lights get the required IP rating or are moved | S |

### E5 — Other ceiling devices

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E5-S1 | Smoke alarms — national rule | Between bedroom zone and rest, hallway serving bedrooms, each storey; clearances | M |
| E5-S2 | Smoke alarm Victorian rules | VIC pack encodes any Victorian variation on the NCC; company policy option for one per bedroom. (Other states, e.g. QLD, are E17) | S |
| E5-S3 | Exhaust fans | Wet rooms and laundry (NCC trigger), rangehood in kitchen; placement near the shower outside forbidden zones | S |
| E5-S4 | Ceiling fans (optional) | Centred; clear radius from lights/alarms; layout re-flows lights around them | S |
| E5-S5 | Collision resolution between ceiling devices | Priority order: smoke alarm > exhaust > fan > downlight; downlights re-flow | M |

### E6 — Switches

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E6-S1 | Entry analysis per room | Entries with connecting room, style, latch side | S |
| E6-S2 | Latch-side placement | Per §8.4; never behind the leaf; walks to nearest free spot | M |
| E6-S3 | Multi-way switching | 2-way / intermediate per policy; hallways | M |
| E6-S4 | Ganging and plate composition | Loads per location combined; gang count ≤ policy max (e.g. 6) | S |
| E6-S5 | Wet-room switch relocation | Out of forbidden zones; outside room when needed | S |
| E6-S6 | Control mapping | Every light/fan has ≥ 1 controlling switch; validator rule | S |

### E7 — Socket outlets and appliance points

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E7-S1 | Policy-driven counts per room type | From `company/default.yaml` | S |
| E7-S2 | Candidate scoring and placement | Per §8.5; deterministic; no hard-rule violations | L |
| E7-S3 | Kitchen bench run | GPOs along bench at spacing; sink/cooktop exclusions; island outlets | M |
| E7-S4 | Appliance points | Fridge, dishwasher, microwave, rangehood, washer, dryer, HWS isolator, AC isolator, cooktop/oven connections | M |
| E7-S5 | Outdoor and garage | Weatherproof outlets; door motor outlet at ceiling | S |
| E7-S6 | (Optional) Low voltage: data, TV, NBN | Separate layer, not on power circuits | S |

### E8 — Loads and maximum demand

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E8-S1 | Point load model | Each point kind → nominal load and Appendix C category | S |
| E8-S2 | Appendix C demand calculation | Tables from the pack; hand-worked reference cases match | M |
| E8-S3 | Per-circuit design current | Used by sizing; diversity per policy | S |
| E8-S4 | Single-phase supply check | Compares max demand with the Victorian distributor's single-phase limit; over the limit → decision required recommending three-phase | S |

### E9 — Circuit grouping

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E9-S1 | Dedicated circuits from appliances | One circuit per appliance per pack | S |
| E9-S2 | Spatial clustering of lighting and GPO circuits | Per §8.6; limits respected; adjacent rooms grouped | L |
| E9-S3 | RCD / RCBO assignment | ≤ 3 circuits per RCD, lighting split; validator rule | M |
| E9-S4 | Circuit naming | Human labels: "Lights — Beds 2/3 & Hall" | S |

### E10 — Cable routing

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E10-S1 | Board origin and roof-space model | Single storey, accessible roof; anything else → decision required | S |
| E10-S2 | Daisy-chain ordering | Nearest neighbour + 2-opt, deterministic | M |
| E10-S3 | Drops and switch loops | Vertical drops; lighting switch loops | S |
| E10-S4 | Lengths and polylines | Per circuit; waste factor; drawn in editor | S |

### E11 — Cable and protection sizing

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E11-S1 | AS/NZS 3008.1.1 tables in the pack | CCC by method, derating, mV/A·m for the cable types used in houses (TPS 1.5–16 mm², XLPE mains) | L |
| E11-S2 | Ib ≤ In ≤ Iz selection | Per §8.8 | M |
| E11-S3 | Voltage drop check with budget split | Mains + subcircuit ≤ 5% total; upsizing | S |
| E11-S4 | Earth fault loop / disconnection time | Appendix B max Zs; length limit; upsizing | M |
| E11-S5 | Consumer mains sizing | Single-phase, from max demand, length and Victorian distributor rules | M |

### E12 — Switchboard

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E12-S1 | Board schedule | Main switch, RCBO/RCD+MCB per circuit, SPD, MEN; pole count + spare | M |
| E12-S2 | Single-line diagram | Generated SVG from the schedule | M |
| E12-S3 | Circuit schedule / labels | Printable schedule matching board order | S |
| E12-S4 | Board location checks | Not in wet area etc.; distance warning for voltage drop | S |

### E13 — Validator and compliance report

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E13-S1 | Validator over any design | Runs every rule; returns violations with severity, items, citation | M |
| E13-S2 | Generator ⊆ validator property | Test: engine output on all reference plans has zero mandatory violations | S |
| E13-S3 | Re-validate after user edits | Fast path (< 200 ms for a typical house) for live editing | M |
| E13-S4 | Compliance report | Per item: rules applied, values; violations; decisions required; standards editions | M |
| E13-S5 | Rule coverage matrix | Every mandatory rule has ≥ 1 passing and ≥ 1 failing test case | S |

### E14 — Editor integration

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E14-S1 | Electrical layer and symbol set | Australian drawing symbols for each point kind; layer toggles (lights, power, zones, circuits, cables) | M |
| E14-S2 | Run engine from editor | Brief → readiness → design; progress per stage | S |
| E14-S3 | Edit points | Move/add/delete; items become `manual`; wall items slide along faces | M |
| E14-S4 | Circuit view | Colour by circuit; reassign a point to another circuit | M |
| E14-S5 | Violations panel | Click a violation → select items; live re-validation | M |
| E14-S6 | Re-run around manual items | Engine keeps manual items and designs the rest | M |
| E14-S7 | Builder and electrician roles | Electrician: may override a mandatory rule only with a recorded reason, shown in the report; marks a design "reviewed". Builder: may move/add/remove points and change policy-tier items, cannot override mandatory rules; any builder edit to a reviewed design clears "reviewed" and lists what changed for the electrician | M |
| E14-S8 | Builder guard-rails and wording | Builder view explains violations in plain language, hides cable/Zs detail by default, and every screen and export carries "design for review and certification by a licensed electrician" | S |

### E15 — Outputs

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E15-S1 | PDF drawing set | Lighting & switching plan, power plan, legend, notes, title block | L |
| E15-S2 | Schedules | Circuit schedule, cable schedule, board schedule | S |
| E15-S3 | BOM (quote-ready) | Counts by item and spec, devices, cable metres by type/size, each line with a stable item code; format versioned and documented so E17 pricing attaches without changes | M |
| E15-S4 | DXF export | Electrical layers over the plan in DXF (follows README §8 export path) | M |

### E16 — Field validation

| ID | Story | Acceptance criteria | Size |
|---|---|---|---|
| E16-S1 | Electrician review workflow | Reviewer marks each item kept / moved / removed / added; stored | M |
| E16-S2 | Acceptance metrics | §1 metrics computed per plan and over the set | S |
| E16-S3 | Policy tuning loop | Changes to `company/default.yaml` re-run against the set; metrics compared | S |
| E16-S4 | Rule-pack sign-off | A Victorian licensed electrician from the panel signs off each mandatory rule value; recorded in `meta.yaml` | M |
| E16-S5 | Panel disagreement handling | Where panel electricians disagree on a reference design, record both; the item is scored as kept if the engine matches either, and the disagreement becomes a policy option | S |

### E17 — Later

- **Planna One ERP integration and quoting** (next iteration, §6.7):
  - E17-S1: map catalogue item codes to Planna product SKUs (preferred + alternatives).
  - E17-S2: `quoting` module with `ProductCatalogue` / `StockLookup` / `PriceLookup`
    ports; Planna adapter using a service token; fake adapter for tests.
  - E17-S3: stock check on a BOM — per line on hand / short / alternative suggested.
  - E17-S4: quotation — materials from the BOM at the customer's Planna price list,
    labour from per-item rates, margin; quote document (PDF).
  - E17-S5: hand-off — push a draft quote to Planna or let Planna pull the BOM
    (decided then); the design keeps working when Planna is down.
- **Three-phase supply**: phase balancing in circuit grouping, three-phase max
  demand and mains, three-phase board.
- **Other states**: NSW, QLD, WA, SA, TAS, ACT, NT state packs (SIRs, smoke alarm laws).
- Two-storey (inter-floor routing), raked ceilings and no-roof-space, solar PV
  (AS/NZS 4777), batteries (AS/NZS 5139), EV chargers, pools and spas,
  smart-home/automation, commercial (Class 2–9).

### Before the engine's first endpoint

Teams, licences and roles (Epic T, [teams-and-licences-plan.md](teams-and-licences-plan.md)), stories
T1–T4, come first: every engine endpoint needs to know which company a request is for and what the
person may do.

## 10. Milestones

Each milestone ends with something an electrician can look at.

| Milestone | Epics | Demo |
|---|---|---|
| **M1 — Foundations & enrichment** | E0, E1 (S1–S6), E2 | Load a plan, add fixtures and a brief, readiness check passes |
| **M2 — Lights and switches** | E3, E4, E5, E6 | Every room lit and switched, wet zones drawn, smoke alarms placed, NCC density checked |
| **M3 — Full points layout** | E7, E13 (S1–S2), E14 (S1–S3) | Complete points layout in the editor, editable, validated |
| **M4 — Circuits and board** | E8, E9, E12 (S1, S3) | Circuits coloured on the plan; board schedule; max demand |
| **M5 — Sized and routed** | E10, E11, E12 (S2, S4), E13 | Cable routes, sizes, VD and Zs per circuit; single-line diagram; compliance report |
| **M6 — Outputs and pilot** | E14 (rest), E15, E16 | PDF set + BOM; 10 reference plans reviewed by electricians; §1 metrics measured |

Recommended first step: **E0-S5 (reference plans) with an electrician**, before
any code beyond E0-S1 — the reference set and the electrician's own designs of
those plans are what every later story is measured against.

## 11. Testing and validation

- **Unit tests** per rule kind and per algorithm, with hand-worked expected values
  (lumen method, Appendix C, VD, Zs).
- **Golden tests**: for each reference plan, the full design is snapshotted as
  JSON; any change shows up as a diff to review, not a silent regression.
- **Property tests** (jqwik): generated rectangular/L-shaped rooms — every
  point inside its room, no item in a forbidden zone, every light controlled,
  every circuit Ib ≤ In ≤ Iz, VD ≤ budget.
- **Generator ⊆ validator**: zero mandatory violations on all reference plans.
- **Determinism**: running twice gives identical bytes.
- **Rule coverage**: every mandatory rule has a passing and a failing case.
- **Electrician benchmark** (E16): each reference plan also designed by an
  electrician; compare engine vs human on the §1 metrics. This is the number that
  says whether we have reached 90%.

## 12. Risks and open questions

| Risk / question | Impact | Mitigation |
|---|---|---|
| Rule values encoded wrongly from the standards | Non-compliant designs | ⚠ markers; licensed copies; electrician sign-off per rule (E16-S4); validator coverage |
| Fixtures missing or misplaced | Wrong wet zones — the most safety-critical output | Readiness check blocks wet-area design; zones drawn visibly; fixture confirmation required |
| Plan scale wrong | Wrong spacing, lengths, VD | Gate on `scaleConfidence`; require calibration below threshold |
| Liability / positioning | Users — especially builders — treat the output as certified | Every output says "design for review by a licensed electrician"; builders cannot override mandatory rules (E14-S7); report lists decisions required |
| Policy varies by electrician | Low acceptance despite compliance | Policy is data; per-company packs; panel of electricians, disagreements recorded (E16-S5); tuning loop (E16-S3) |
| Distributor variation within Victoria | Wrong mains/Ze/supply limit | One plan per distributor in the reference set; distributor is a required brief field |
| All-electric homes exceed single-phase limit | v1 can't finish the design | Detected and raised as a decision; three-phase is E17 |
| FloorPlan contract drifts across Python, TypeScript and Java | Engine misreads plans | JSON Schema as source of truth; contract tests in all three builds (§6.6) |
| Two backends to run and deploy (vision + engine) | Ops overhead | Same container/deployment pattern as Planna One; engine is stateless apart from Postgres; the editor talks to both through one proxy |
| Planna One API not ready or changes when quoting starts | Quoting delayed | Planna sits behind ports in the `quoting` module only; BOM is a versioned contract; design and BOM never depend on Planna |
| Roof construction unknown from a plan | Routing and derating guesses | Brief fields; decision required when unknown |
| AS/NZS 3000 revision in progress | Rework | Edition pinned per pack; packs versioned |

**Answered** (see [v1 scope decisions](#v1-scope-decisions)): Victoria first; a
panel of electricians reviews; users are builders and electricians; quoting later
on a quote-ready BOM; single-phase only.

Also answered:
- **Panel**: confirmed, including a Victorian licensed electrician for rule-pack
  sign-off. Names to be recorded in `meta.yaml` when E16-S4 starts.
- **Default policy**: a **neutral** pack (`company/default.yaml`) built from common
  practice and the panel's agreement, not any one builder's standard. Each company
  gets its own override file (`company/<company>.yaml`) on top of it (E0-S7).

**Still open:**
1. **Licensed copies of the standards — nobody holds them yet.** See E0-S8. Until
   they are obtained, every ⚠ value stays a placeholder and no rule pack can be
   marked "release"; development and tests can proceed on the placeholders.

## 13. Glossary

| Term | Meaning |
|---|---|
| AFL | Above finished floor |
| CCC / Iz | Current-carrying capacity of a cable, after derating |
| DNSP | Distribution network service provider (the local electricity distributor) |
| GPO | General power outlet (socket outlet) |
| IC-4 | Recessed luminaire rating: may be abutted and covered by insulation |
| Ib / In | Design current of a circuit / rated current of its protective device |
| MCB | Miniature circuit breaker |
| MEN | Multiple earthed neutral — the Australian earthing system |
| RCD / RCBO | Residual current device / combined RCD and MCB |
| SELV | Separated extra-low voltage |
| SIR | Service and installation rules (state-specific) |
| SPD | Surge protective device |
| TPS | Thermoplastic-sheathed cable (flat twin & earth) |
| VD | Voltage drop |
| Zs / Ze | Earth fault loop impedance, total / external to the installation |
