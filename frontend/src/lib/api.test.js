import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('./supabase', () => ({
  supabase: { auth: { getSession: async () => ({ data: { session: { access_token: 'tok' } } }) } },
}))

import { api, setCompany } from './api'

function respond(status, body) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
    body === null ? null : JSON.stringify(body),
    { status, headers: { 'content-type': 'application/json' } },
  )))
}

describe('api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends the Supabase access token as a Bearer token', async () => {
    respond(200, { ok: true })
    expect(await api('/api/me')).toEqual({ ok: true })
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer tok')
  })

  it('throws the API\'s own message with the status', async () => {
    respond(400, { message: 'Not a valid date' })
    await expect(api('/api/things', { method: 'POST', json: {} })).rejects.toMatchObject({
      message: 'Not a valid date', status: 400,
    })
  })

  it('falls back to a plain message when the API gives none', async () => {
    respond(502, {})
    await expect(api('/api/me')).rejects.toMatchObject({ status: 502, message: expect.stringMatching(/did not answer/) })
  })

  it('sends the chosen company with every call, and nothing when none is chosen', async () => {
    respond(200, {})
    await api('/api/projects')
    expect(fetch.mock.calls[0][1].headers['X-Organisation-Id']).toBeUndefined()

    setCompany('c0ffee00-0000-4000-8000-000000000001')
    respond(200, {})
    await api('/api/projects')
    expect(fetch.mock.calls[0][1].headers['X-Organisation-Id']).toBe('c0ffee00-0000-4000-8000-000000000001')

    setCompany(null)
    respond(200, {})
    await api('/api/projects')
    expect(fetch.mock.calls[0][1].headers['X-Organisation-Id']).toBeUndefined()
  })

  it('sends a form as multipart, leaving its type to the browser', async () => {
    respond(200, {})
    const form = new FormData()
    form.append('file', new Blob(['png']), 'plan.png')
    await api('/api/houses/h1/floor-plan/uploads', { method: 'POST', form })
    const [, init] = fetch.mock.calls[0]
    expect(init.body).toBe(form)
    expect(init.headers['Content-Type']).toBeUndefined()
    expect(init.headers.Authorization).toBe('Bearer tok')
  })

  it('returns null for 204 No Content', async () => {
    respond(204, null)
    expect(await api('/api/things/1', { method: 'DELETE' })).toBeNull()
  })
})
