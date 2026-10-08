// @vitest-environment node
/// <reference types="node" />
// Node's types only here: this test reads the contract files from disk.
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import Ajv2020 from 'ajv/dist/2020'
import { describe, expect, it } from 'vitest'
import type { DesignPoint, ElectricalDesign, Fixture, ProjectBrief } from './contracts'

// The same contracts the API is tested against (backend ContractTest), checked
// from the browser's side: the frontend's validator agrees on every example,
// and the generated types match what the schemas allow.

const contracts = fileURLToPath(new URL('../../../contracts', import.meta.url))
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const json = (path: string): any => JSON.parse(readFileSync(path, 'utf8'))

const ajv = new Ajv2020({ allErrors: true, strict: true })
ajv.addSchema(json(join(contracts, 'common.schema.json')), 'common.schema.json')
const documents = ['project-brief', 'fixture', 'electrical-design'] as const
const validators = Object.fromEntries(
  documents.map((d) => [d, ajv.compile(json(join(contracts, `${d}.schema.json`)))]),
)

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function examples(document: string, kind: 'valid' | 'invalid'): [string, any][] {
  const dir = join(contracts, 'examples', document, kind)
  return readdirSync(dir)
    .filter((f: string) => f.endsWith('.json'))
    .map((f: string) => [f, json(join(dir, f))])
}

describe.each(documents)('%s contract', (document) => {
  const validate = validators[document]

  it('has valid and invalid examples', () => {
    expect(examples(document, 'valid').length).toBeGreaterThan(0)
    expect(examples(document, 'invalid').length).toBeGreaterThan(0)
  })

  it.each(examples(document, 'valid'))('accepts %s', (_name: string, example: unknown) => {
    expect(validate(example), JSON.stringify(validate.errors)).toBe(true)
  })

  it.each(examples(document, 'invalid'))('refuses %s', (_name: string, example: unknown) => {
    expect(validate(example)).toBe(false)
  })
})

describe('generated types', () => {
  it('describe a valid design, and the schema agrees', () => {
    const point: DesignPoint = {
      id: 'lt_001',
      kind: 'downlight',
      placement: { type: 'ceiling', at: { x: 1200, y: 900 } },
      spec: { watts: 10, ic: 'IC-4' },
      rationale: [],
      source: 'engine',
    }
    const design: ElectricalDesign = {
      version: 1,
      planRef: { planHash: `sha256:${'a'.repeat(64)}`, planVersion: 1 },
      rulePack: { id: 'au-residential', version: '2026.1', state: 'VIC', standards: [] },
      points: [point],
      zones: [],
      circuits: [],
      violations: [],
      decisionsRequired: [],
    }
    expect(validators['electrical-design'](design), JSON.stringify(validators['electrical-design'].errors)).toBe(true)
  })

  it('narrow placements by their type', () => {
    const point = examples('electrical-design', 'valid')[0][1].points[3] as DesignPoint
    if (point.placement.type === 'wall') {
      expect(point.placement.height).toBeGreaterThan(0)
    } else {
      throw new Error('expected a wall placement')
    }
  })

  it('reject what the schema rejects, at compile time', () => {
    // These lines must not compile; tsc (npm run typecheck) fails if they do.
    // @ts-expect-error: not a point kind
    const kind: DesignPoint['kind'] = 'chandelier'
    // @ts-expect-error: briefs need appliances
    const brief: ProjectBrief = { version: 1, state: 'VIC', distributor: 'jemena', supply: { phases: 1, nominalVoltage: 230 }, construction: { storeys: 1, defaultCeilingHeight: 2550, ceilingInsulated: true, roofSpaceAccessible: true, slab: true } }
    // @ts-expect-error: fixtures come from people or vision, nothing else
    const source: Fixture['source'] = 'engine'
    expect([kind, brief, source]).toHaveLength(3)
  })
})
