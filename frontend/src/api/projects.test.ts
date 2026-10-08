// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { STAGE_LABELS, ago, fieldErrors, queryString, stageTone } from './projects'

describe('projects helpers', () => {
  it('builds a query string from the values that are set', () => {
    expect(queryString({ q: 'smith st', status: undefined, archived: true, page: 0, empty: '' })).toBe('?q=smith+st&archived=true&page=0')
    expect(queryString({})).toBe('')
  })

  it('says how long ago something changed', () => {
    const now = new Date('2026-10-08T12:00:00Z')
    expect(ago('2026-10-08T11:59:30Z', now)).toBe('just now')
    expect(ago('2026-10-08T11:45:00Z', now)).toBe('15 min ago')
    expect(ago('2026-10-08T09:00:00Z', now)).toBe('3 h ago')
    expect(ago('2026-10-07T09:00:00Z', now)).toBe('yesterday')
    expect(ago('2026-10-04T12:00:00Z', now)).toBe('4 days ago')
    expect(ago('2026-09-01T12:00:00Z', now)).toMatch(/2026/)
  })

  it('turns the API’s field errors into a lookup by field', () => {
    const error = { body: { errors: [{ field: 'site.state', message: 'Choose the state' }] } }
    expect(fieldErrors(error)).toEqual({ 'site.state': 'Choose the state' })
    expect(fieldErrors(new Error('plain'))).toEqual({})
  })

  it('names and colours every stage', () => {
    expect(Object.keys(STAGE_LABELS)).toHaveLength(14)
    expect(stageTone('awaiting_upload')).toBe('todo')
    expect(stageTone('analysing')).toBe('active')
    expect(stageTone('won')).toBe('done')
    expect(stageTone('changes_requested')).toBe('warn')
    expect(stageTone('archived')).toBe('muted')
  })
})
