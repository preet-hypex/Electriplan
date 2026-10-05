import { chmodSync, existsSync, readFileSync, writeFileSync } from 'node:fs'

const ASSIGNMENT = /^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$/

function unquote(raw) {
  const value = raw.trim()
  const quoted = /^"(.*)"$/.exec(value) || /^'(.*)'$/.exec(value)
  if (!quoted) return value.replace(/\s+#.*$/, '')
  return value.startsWith('"') ? quoted[1].replace(/\\(.)/g, '$1') : quoted[1]
}

export function render(value) {
  return /^[A-Za-z0-9_@%+=:,./-]+$/.test(value) ? value : `"${value.replace(/[\\"`$]/g, '\\$&')}"`
}

export function parseEnv(text) {
  const values = {}
  for (const line of text.split(/\r?\n/)) {
    const match = ASSIGNMENT.exec(line)
    if (match) values[match[1]] = unquote(match[2])
  }
  return values
}

export function readEnv(file) {
  return existsSync(file) ? parseEnv(readFileSync(file, 'utf8')) : {}
}

export function upsertEnv(text, entries, header = []) {
  const pending = new Map(Object.entries(entries).filter(([, v]) => v !== undefined && v !== null && v !== ''))
  const lines = text ? text.replace(/\n$/, '').split(/\r?\n/) : [...header]
  const out = lines.map(line => {
    const match = ASSIGNMENT.exec(line)
    if (!match || !pending.has(match[1])) return line
    const value = pending.get(match[1])
    pending.delete(match[1])
    return `${match[1]}=${render(value)}`
  })
  for (const [key, value] of pending) out.push(`${key}=${render(value)}`)
  return out.join('\n') + '\n'
}

export function writeEnv(file, entries, header) {
  const before = existsSync(file) ? readFileSync(file, 'utf8') : ''
  writeFileSync(file, upsertEnv(before, entries, header), { mode: 0o600 })
  chmodSync(file, 0o600)
}
