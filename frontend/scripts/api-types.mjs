// Generates src/api/schema.ts from ../contracts/openapi.json (the Java API's
// OpenAPI document), so the web app's API types are the API's own.
//
//   node scripts/api-types.mjs           write the file
//   node scripts/api-types.mjs --check   fail if the file is out of date (CI)
//
// contracts/openapi.json itself comes from the backend: OpenApiContractTest
// fails when it is stale and rewrites it with -Dopenapi.write=true.
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import openapiTS, { astToString } from 'openapi-typescript'

const here = dirname(fileURLToPath(import.meta.url))
const source = resolve(here, '../../contracts/openapi.json')
const out = resolve(here, '../src/api/schema.ts')

const banner = `/* eslint-disable */
/**
 * Generated from contracts/openapi.json by scripts/api-types.mjs.
 * Do not edit by hand: change the API, regenerate contracts/openapi.json
 * (see contracts/README.md), then run \`npm run contracts\`.
 */
`

const ast = await openapiTS(pathToFileURL(source), { alphabetize: true, exportType: true })
const generated = banner + astToString(ast)

if (process.argv.includes('--check')) {
  let current = ''
  try { current = readFileSync(out, 'utf8') } catch { /* missing */ }
  if (current !== generated) {
    console.error('src/api/schema.ts is out of date with contracts/openapi.json. Run: npm run contracts')
    process.exit(1)
  }
  console.log('src/api/schema.ts is up to date')
} else {
  writeFileSync(out, generated)
  console.log(`wrote ${out}`)
}
