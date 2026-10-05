import { afterEach, describe, expect, it, vi } from 'vitest'

async function loadWith(env) {
  vi.resetModules()
  for (const [k, v] of Object.entries(env)) vi.stubEnv(k, v)
  return (await import('./supabase')).configError
}

const VALID = {
  VITE_SUPABASE_URL: 'https://abcdefghijklmnop.supabase.co',
  VITE_SUPABASE_PUBLISHABLE_KEY: 'sb_publishable_exampleKey123',
}

describe('configError', () => {
  afterEach(() => { vi.unstubAllEnvs(); delete window.__APP_ENV__ })

  it('is null when both values are present and well formed', async () => {
    expect(await loadWith(VALID)).toBeNull()
  })

  it('takes the values a hosted build wrote to /env.js over the ones built in', async () => {
    window.__APP_ENV__ = VALID
    expect(await loadWith({ VITE_SUPABASE_URL: '', VITE_SUPABASE_PUBLISHABLE_KEY: '' })).toBeNull()
    window.__APP_ENV__ = { VITE_SUPABASE_URL: 'http://localhost:8080' }
    expect(await loadWith(VALID)).toMatch(/does not look like a project URL/)
  })

  it('points at the setup script when nothing is configured', async () => {
    expect(await loadWith({ ...VALID, VITE_SUPABASE_URL: '' })).toMatch(/npm run setup/)
  })

  it('refuses a secret key outright, so it never reaches a browser', async () => {
    expect(await loadWith({ ...VALID, VITE_SUPABASE_PUBLISHABLE_KEY: 'sb_secret_oops' })).toMatch(/holds a secret key/)
  })

  it('rejects a legacy anon key', async () => {
    expect(await loadWith({ ...VALID, VITE_SUPABASE_PUBLISHABLE_KEY: 'eyJhbGciOiJIUzI1NiJ9.legacy' })).toMatch(/not a publishable key/)
  })
})

describe('readAuthRedirectError', () => {
  async function read(href) {
    const { readAuthRedirectError } = await import('./supabase')
    return readAuthRedirectError(href)
  }

  it('reads the reason from the fragment, where Supabase puts it for an expired email link', async () => {
    expect(await read('http://localhost:5180/invite#error=access_denied&error_code=otp_expired'
      + '&error_description=Email+link+is+invalid+or+has+expired'))
      .toBe('Email link is invalid or has expired')
  })

  it('reads it from the query string as well', async () => {
    expect(await read('http://localhost:5180/login?error=server_error&error_description=Something+broke')).toBe('Something broke')
  })

  it('still says something when only an error code came back', async () => {
    expect(await read('http://localhost:5180/invite#error=access_denied')).toBe('That link could not be used')
  })

  it('is null for an ordinary address, and for a link that worked', async () => {
    expect(await read('http://localhost:5180/login')).toBeNull()
    expect(await read('http://localhost:5180/invite#access_token=abc&type=invite')).toBeNull()
  })
})
