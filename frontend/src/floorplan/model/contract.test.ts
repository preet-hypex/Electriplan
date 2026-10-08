// @vitest-environment node
/// <reference types="node" />
// Node's types only here: this test reads the contract files from disk.
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import Ajv2020 from 'ajv/dist/2020'
import { describe, expect, it } from 'vitest'
import { createDoor, createLabel, createRoom, createWall, createWindow } from './factory'
import { sampleFloorPlan } from './sample'
import { parseFloorPlan, parseFloorPlanJson, serialiseFloorPlan } from './serialise'
import { FLOORPLAN_VERSION, emptyFloorPlan, type Door, type FloorPlan, type Wall } from './types'

// The editor's FloorPlan against contracts/floor-plan.schema.json, the same
// contract the analyser (floorplan/tests/test_contract.py) and the engine
// (backend ContractTest, FloorPlanReaderTest) are checked against. The types in
// ./types are generated from it; these check what the editor actually builds.

const contracts = fileURLToPath(new URL('../../../../contracts', import.meta.url))
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const json = (path: string): any => JSON.parse(readFileSync(path, 'utf8'))

const ajv = new Ajv2020({ allErrors: true, strict: true })
ajv.addSchema(json(join(contracts, 'common.schema.json')), 'common.schema.json')
const validate = ajv.compile(json(join(contracts, 'floor-plan.schema.json')))

function expectValid(plan: unknown): void {
  // Through JSON, as it is saved and sent: undefined optionals disappear.
  const sent = JSON.parse(JSON.stringify(plan))
  expect(validate(sent), JSON.stringify(validate.errors)).toBe(true)
}

const valid = readdirSync(join(contracts, 'examples/floor-plan/valid'))
  .filter((f: string) => f.endsWith('.json'))
  .map((f: string) => [f, json(join(contracts, 'examples/floor-plan/valid', f))] as const)

describe('the editor and the FloorPlan contract', () => {
  it('agree on the version', () => {
    expect(FLOORPLAN_VERSION).toBe(json(join(contracts, 'floor-plan.schema.json')).properties.version.const)
  })

  it('an empty plan is valid', () => {
    expectValid(emptyFloorPlan())
  })

  it('the sample plan is valid', () => {
    expectValid(sampleFloorPlan())
  })

  it('what the editor draws is valid', () => {
    const wall = createWall({ x: 0, y: 0 }, { x: 4000, y: 0 })
    const plan: FloorPlan = {
      ...emptyFloorPlan(),
      walls: [wall],
      rooms: [createRoom('BED 1', [{ x: 0, y: 0 }, { x: 4000, y: 0 }, { x: 4000, y: 3000 }], { x: 2000, y: 1000 })],
      doors: [createDoor(wall.id, 1000)],
      windows: [createWindow(wall.id, 3000)],
      labels: [createLabel('Robe', { x: 500, y: 500 })],
    }
    expectValid(plan)
  })

  it.each(valid)('reads %s, writes it back unchanged and valid', (_name: string, example: unknown) => {
    const written = JSON.parse(serialiseFloorPlan(parseFloorPlanJson(JSON.stringify(example))))
    expectValid(written)
    expect(written).toEqual(example)
  })

  it('refuses a version the contract does not list', () => {
    expect(() => parseFloorPlan({ ...emptyFloorPlan(), version: 2 })).toThrow(/version/)
    expect(() => parseFloorPlan({ ...emptyFloorPlan(), version: 0 })).toThrow(/version/)
  })

  it('refuses a door swing that is not a side, and a room without three corners', () => {
    const wall: Wall = { id: 'w1', start: { x: 0, y: 0 }, end: { x: 1, y: 0 }, thickness: 90 }
    expect(() =>
      parseFloorPlan({ walls: [wall], doors: [{ id: 'd1', wallId: 'w1', position: 0, width: 800, swing: 45 }] }),
    ).toThrow(/doors\[0\]\.swing/)
    expect(() =>
      parseFloorPlan({ rooms: [{ id: 'r1', name: '', polygon: [{ x: 0, y: 0 }, { x: 1, y: 1 }], labelPosition: { x: 0, y: 0 } }] }),
    ).toThrow(/rooms\[0\]\.polygon/)
  })

  it('the generated types refuse what the schema refuses, at compile time', () => {
    // These lines must not compile; tsc (npm run typecheck) fails if they do.
    // @ts-expect-error: a door opens to one side or the other
    const swing: Door['swing'] = 45
    // @ts-expect-error: the engine never places items in a floor plan
    const source: Wall['source'] = 'engine'
    // @ts-expect-error: a room needs three corners
    const polygon: FloorPlan['rooms'][number]['polygon'] = [{ x: 0, y: 0 }, { x: 1, y: 0 }]
    // @ts-expect-error: only version 1 exists
    const version: FloorPlan['version'] = 2
    expect([swing, source, polygon, version]).toHaveLength(4)
  })
})
