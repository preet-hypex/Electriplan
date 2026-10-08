# Contracts

The data the electrical engine reads and writes, as JSON Schemas (draft 2020-12). They are the
source of truth: the Java model, the editor's TypeScript types, the floor-plan analyser's models and
the database's JSON documents all follow them.

| Schema | Describes | Java | TypeScript |
|---|---|---|---|
| [`floor-plan.schema.json`](floor-plan.schema.json) | The plan the engine designs from: walls, rooms, doors, windows, openings, labels, dimensions, the source image and scale. Written by the analyser (`floorplan/`), edited in the editor, only read by the engine | `model.plan.FloorPlan`, read by `FloorPlanReader` | `FloorPlan` (re-exported by `floorplan/model/types.ts`) |
| [`project-brief.schema.json`](project-brief.schema.json) | What the plan can't say: supply, construction, appliances, preferences | `model.brief.ProjectBrief` | `ProjectBrief` |
| [`fixture.schema.json`](fixture.schema.json) | Showers, basins, benches, appliances, switchboard: what zones, outlets and circuits depend on | `model.fixture.Fixture` | `Fixture` |
| [`electrical-design.schema.json`](electrical-design.schema.json) | The design: points, zones, circuits, switchboard, demand, violations, decisions | `model.design.ElectricalDesign` | `ElectricalDesign` |
| [`common.schema.json`](common.schema.json) | Shared parts: ids, points, millimetres, wall anchors | `model.common`, `model.units` | `Point`, `WallAnchor`... |

**Fixed lists versus configurable data.** Values the engine has rules for (cooktop and hot-water
types, fixture, point and zone kinds...) are enums: a new value needs new engine behaviour, so it is a
code change, made in the schema, the Java model and the rules together. Reference data that grows
without new logic is configurable: the **distributor** is a code (`jemena`) checked against the
`electricity_distributor` table, listed by `GET /api/reference/distributors?state=VIC`. Adding a
distributor is a database row, not a release. The schema only checks such codes are well formed.

**States.** The contracts are national: every Australian state is allowed, and nothing is limited to
Victoria. A design records which state's rules made it (`rulePack.state`); which states the engine
actually designs is decided by the rule pack, not the contracts (see "Expanding beyond Victoria" in
the engine plan).

Java packages are under `com.hypex.electriplan` in `backend/`; the TypeScript types are generated
into `frontend/src/electrical/contracts.ts`, and the editor's FloorPlan types are those generated
ones. The Python analyser's pydantic models (`floorplan/app/models.py`) are hand-written and tested
against the schema. Lengths are millimetres unless the property name says otherwise (`lengthM`
metres, `demandA` amperes, `csaMm2` mm²).

## Examples

`examples/<document>/valid/*.json` must be accepted, and `examples/<document>/invalid/*.json`
refused, by the schema **and** by the Java model **and** by the frontend's validator. They are
also what the round-trip tests read and write back unchanged; floor-plan examples are round-tripped
by the analyser's models and the editor's parser too. Add an example for every new rule or field: a
valid one showing it, an invalid one breaking it.

## Changing a contract

1. Change the schema (and add examples).
2. Change the Java record in `backend/.../model` to match. `EnumParityTest` and `ContractTest`
   fail until the two agree.
3. Regenerate the TypeScript types: `cd frontend && npm run contracts`. CI fails if they are stale.
4. For the FloorPlan, change `floorplan/app/models.py` too; `tests/test_contract.py` fails until it
   writes what the schema allows.
5. Bump `version` (a `const` in each document) for a change that old documents would not survive,
   and teach readers the old version. For the FloorPlan that means adding it to
   `FloorPlan.SUPPORTED_VERSIONS`: until then `FloorPlanReader` refuses the new version with an
   `UnsupportedFloorPlanVersionException`, and the editor and the analyser refuse it too.

The schemas are packaged into the API (`classpath:contracts/`), because `FloorPlanReader` checks
every incoming plan against `floor-plan.schema.json`. The Docker build gets them as the `contracts`
build context (`docker-compose.yml` sets it).

## Tests

| Where | What |
|---|---|
| `backend/.../model/ContractTest` | Schemas are valid 2020-12; every example accepted or refused as expected; Java reads and writes every valid example back unchanged and refuses every invalid one; what Java builds is valid |
| `backend/.../model/EnumParityTest` | Every Java enum lists exactly its schema's values |
| `backend/.../model/FloorPlanReaderTest` | Every valid plan is read; every invalid one refused with its problems listed; any version but 1 refused as unsupported |
| `backend/.../model/*ModelTest` (incl. `PlanModelTest`), `UnitsTest`, `ChecksTest`, `ModelJsonTest` | Every record's rules, the unit types, strict JSON |
| `backend/.../model/ModelModuleIntegrationTests` | In the running application: the model is an open module with no dependencies, and Spring's own mapper writes schema-valid documents |
| `frontend/src/electrical/contracts.test.ts` | The frontend's validator (Ajv) agrees on every example; the generated types accept a valid design and refuse invalid values at compile time |
| `frontend/src/floorplan/model/contract.test.ts` | The editor's empty, sample and hand-drawn plans are valid; its parser reads every valid example and writes it back unchanged, and refuses other versions |
| `floorplan/tests/test_contract.py` | What the analyser really returns (pipeline and `/analyse`, on a drawn plan) is valid; its models read and write back every valid example unchanged; `/export` refuses other versions |
