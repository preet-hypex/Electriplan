# Contracts

The data the electrical engine reads and writes, as JSON Schemas (draft 2020-12). They are the
source of truth: the Java model, the editor's TypeScript types and the database's JSON documents all
follow them.

| Schema | Describes | Java | TypeScript |
|---|---|---|---|
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

Java packages are under `com.hypex.electriplan` in `backend/`; the TypeScript types are generated
into `frontend/src/electrical/contracts.ts`. Lengths are millimetres unless the property name says
otherwise (`lengthM` metres, `demandA` amperes, `csaMm2` mm²).

## Examples

`examples/<document>/valid/*.json` must be accepted, and `examples/<document>/invalid/*.json`
refused, by the schema **and** by the Java model **and** by the frontend's validator. They are
also what the round-trip tests read and write back unchanged. Add an example for every new rule or
field: a valid one showing it, an invalid one breaking it.

## Changing a contract

1. Change the schema (and add examples).
2. Change the Java record in `backend/.../model` to match. `EnumParityTest` and `ContractTest`
   fail until the two agree.
3. Regenerate the TypeScript types: `cd frontend && npm run contracts`. CI fails if they are stale.
4. Bump `version` (a `const` in each document) for a change that old documents would not survive,
   and teach readers the old version.

## Tests

| Where | What |
|---|---|
| `backend/.../model/ContractTest` | Schemas are valid 2020-12; every example accepted or refused as expected; Java reads and writes every valid example back unchanged and refuses every invalid one; what Java builds is valid |
| `backend/.../model/EnumParityTest` | Every Java enum lists exactly its schema's values |
| `backend/.../model/*ModelTest`, `UnitsTest`, `ChecksTest`, `ModelJsonTest` | Every record's rules, the unit types, strict JSON |
| `backend/.../model/ModelModuleIntegrationTests` | In the running application: the model is an open module with no dependencies, and Spring's own mapper writes schema-valid documents |
| `frontend/src/electrical/contracts.test.ts` | The frontend's validator (Ajv) agrees on every example; the generated types accept a valid design and refuse invalid values at compile time |
