import { describe, expect, it } from 'vitest'
import { parseEnv, render, upsertEnv } from './envfile.mjs'
import { pickKeys, planAuthChanges } from './management.mjs'

describe('upsertEnv', () => {
  it('starts a new file with the header', () => {
    expect(upsertEnv('', { A: '1' }, ['# hi'])).toBe('# hi\nA=1\n')
  })

  it('replaces managed keys in place and keeps everything else', () => {
    const before = '# mine\nVITE_SUPABASE_URL=old\nVITE_EXTRA=keep\n'
    expect(upsertEnv(before, { VITE_SUPABASE_URL: 'https://x.supabase.co', VITE_NEW: 'n' }))
      .toBe('# mine\nVITE_SUPABASE_URL=https://x.supabase.co\nVITE_EXTRA=keep\nVITE_NEW=n\n')
  })

  it('leaves a key alone rather than blanking it when there is no value', () => {
    expect(upsertEnv('A=1\n', { A: '' })).toBe('A=1\n')
  })

  it('quotes values that need it, and reads them back unchanged', () => {
    const value = 'has space "and" $dollar'
    expect(render('sb_publishable_abc-123')).toBe('sb_publishable_abc-123')
    expect(parseEnv(`K=${render(value)}`).K).toBe(value)
  })
})

describe('pickKeys', () => {
  it('takes the revealed publishable and secret keys and ignores legacy JWTs and masked values', () => {
    expect(pickKeys([
      { type: 'legacy', name: 'anon', api_key: 'eyJhbGciOi.legacy' },
      { type: 'secret', name: 'masked', api_key: 'sb_secret_abcd••••••••' },
      { type: 'publishable', name: 'default', api_key: 'sb_publishable_pub123' },
      { type: 'secret', name: 'default', api_key: 'sb_secret_sec456' },
    ])).toEqual({ publishable: 'sb_publishable_pub123', secret: 'sb_secret_sec456' })
  })

  it('reports what is missing', () => {
    expect(pickKeys([{ type: 'legacy', api_key: 'eyJ' }])).toEqual({ publishable: null, secret: null })
    expect(pickKeys(null)).toEqual({ publishable: null, secret: null })
  })
})

describe('planAuthChanges', () => {
  const redirectUrls = ['http://localhost:5180/**', 'http://localhost:4180/**']

  it('adds only the redirect URLs that are missing and keeps the existing ones', () => {
    const { changes, added } = planAuthChanges(
      { site_url: 'https://app.example.com', uri_allow_list: 'https://app.example.com/**,http://localhost:5180/**', disable_signup: true },
      { redirectUrls, siteUrl: 'http://localhost:5180', inviteOnly: false },
    )
    expect(added).toEqual(['http://localhost:4180/**'])
    expect(changes).toEqual({ uri_allow_list: 'https://app.example.com/**,http://localhost:5180/**,http://localhost:4180/**' })
  })

  it('never overwrites a real site URL, but replaces the default placeholder', () => {
    const keep = planAuthChanges({ site_url: 'https://prod.example.com', uri_allow_list: '' }, { redirectUrls: [], siteUrl: 'http://localhost:5180' })
    expect(keep.changes.site_url).toBeUndefined()
    const replace = planAuthChanges({ site_url: 'http://localhost:3000', uri_allow_list: '' }, { redirectUrls: [], siteUrl: 'http://localhost:5180' })
    expect(replace.changes.site_url).toBe('http://localhost:5180')
  })

  it('turns sign-ups off only when asked and only when they are on', () => {
    expect(planAuthChanges({ disable_signup: false }, { redirectUrls: [], inviteOnly: true }).changes).toEqual({ disable_signup: true })
    expect(planAuthChanges({ disable_signup: true }, { redirectUrls: [], inviteOnly: true }).changes).toEqual({})
  })

  it('changes nothing when everything is already in place', () => {
    const { changes } = planAuthChanges(
      { site_url: 'http://localhost:5180', uri_allow_list: redirectUrls.join(','), disable_signup: true },
      { redirectUrls, siteUrl: 'http://localhost:5180', inviteOnly: true },
    )
    expect(changes).toEqual({})
  })
})
