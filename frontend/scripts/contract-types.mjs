// Generates src/electrical/contracts.ts from the JSON Schemas in ../contracts,
// so the editor's types are the contract's types.
//
//   node scripts/contract-types.mjs           write the file
//   node scripts/contract-types.mjs --check   fail if the file is out of date (CI)
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { compile } from 'json-schema-to-typescript'

const here = dirname(fileURLToPath(import.meta.url))
const contracts = resolve(here, '../../contracts')
const out = resolve(here, '../src/electrical/contracts.ts')

// One schema that points at each document, so shared definitions (Point,
// WallAnchor...) are generated once and every document type is exported.
const bundle = {
  title: 'Contracts',
  type: 'object',
  properties: {
    projectBrief: { $ref: 'project-brief.schema.json' },
    fixture: { $ref: 'fixture.schema.json' },
    electricalDesign: { $ref: 'electrical-design.schema.json' },
  },
  additionalProperties: false,
}

const banner = `/* eslint-disable */
/**
 * Generated from contracts/*.schema.json by scripts/contract-types.mjs.
 * Do not edit by hand: change the schema and run \`npm run contracts\`.
 */`

const generated = await compile(bundle, 'Contracts', {
  cwd: contracts,
  bannerComment: banner,
  additionalProperties: false,
  unreachableDefinitions: true,
  style: { semi: false, singleQuote: true },
})

if (process.argv.includes('--check')) {
  let current = ''
  try { current = readFileSync(out, 'utf8') } catch { /* missing */ }
  if (current !== generated) {
    console.error('src/electrical/contracts.ts is out of date with contracts/*.schema.json. Run: npm run contracts')
    process.exit(1)
  }
  console.log('src/electrical/contracts.ts is up to date')
} else {
  writeFileSync(out, generated)
  console.log(`wrote ${out}`)
}
