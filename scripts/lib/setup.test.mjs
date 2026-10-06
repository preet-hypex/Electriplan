import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { parseEnv, render, upsertEnv } from './envfile.mjs'
import { pickKeys, planAuthChanges } from './management.mjs'
import { supabaseAdmin } from './stack.mjs'

describe('upsertEnv', () => {
  it('starts a new file from the header or template', () => {
    assert.equal(upsertEnv('', { A: '1' }, ['# hi']), '# hi\nA=1\n')
    assert.equal(upsertEnv('', { A: '1' }, ['# t', 'A=', '# B=2']), '# t\nA=1\n# B=2\n')
  })

  it('replaces managed keys in place and keeps everything else', () => {
    const before = '# mine\nVITE_SUPABASE_URL=old\nVITE_EXTRA=keep\n'
    assert.equal(upsertEnv(before, { VITE_SUPABASE_URL: 'https://x.supabase.co', VITE_NEW: 'n' }),
      '# mine\nVITE_SUPABASE_URL=https://x.supabase.co\nVITE_EXTRA=keep\nVITE_NEW=n\n')
  })

  it('leaves a key alone rather than blanking it when there is no value', () => {
    assert.equal(upsertEnv('A=1\n', { A: '' }), 'A=1\n')
  })

  it('quotes values that need it, and reads them back unchanged', () => {
    const value = 'has space "and" $dollar'
    assert.equal(render('sb_publishable_abc-123'), 'sb_publishable_abc-123')
    assert.equal(parseEnv(`K=${render(value)}`).K, value)
    assert.equal(parseEnv('APP_NAME="Your App"').APP_NAME, 'Your App')
  })
})

describe('pickKeys', () => {
  it('takes the revealed publishable and secret keys and ignores legacy JWTs and masked values', () => {
    assert.deepEqual(pickKeys([
      { type: 'legacy', name: 'anon', api_key: 'eyJhbGciOi.legacy' },
      { type: 'secret', name: 'masked', api_key: 'sb_secret_abcd••••••••' },
      { type: 'publishable', name: 'default', api_key: 'sb_publishable_pub123' },
      { type: 'secret', name: 'default', api_key: 'sb_secret_sec456' },
    ]), { publishable: 'sb_publishable_pub123', secret: 'sb_secret_sec456' })
  })

  it('reports what is missing', () => {
    assert.deepEqual(pickKeys([{ type: 'legacy', api_key: 'eyJ' }]), { publishable: null, secret: null })
    assert.deepEqual(pickKeys(null), { publishable: null, secret: null })
  })
})

describe('planAuthChanges', () => {
  const redirectUrls = ['http://localhost:5180/**', 'http://localhost:4180/**']

  it('adds only the redirect URLs that are missing and keeps the existing ones', () => {
    const { changes, added } = planAuthChanges(
      { site_url: 'https://app.example.com', uri_allow_list: 'https://app.example.com/**,http://localhost:5180/**', disable_signup: true },
      { redirectUrls, siteUrl: 'http://localhost:5180', inviteOnly: false },
    )
    assert.deepEqual(added, ['http://localhost:4180/**'])
    assert.deepEqual(changes, { uri_allow_list: 'https://app.example.com/**,http://localhost:5180/**,http://localhost:4180/**' })
  })

  it('never overwrites a real site URL, but replaces the default placeholder', () => {
    const keep = planAuthChanges({ site_url: 'https://prod.example.com', uri_allow_list: '' }, { redirectUrls: [], siteUrl: 'http://localhost:5180' })
    assert.equal(keep.changes.site_url, undefined)
    const replace = planAuthChanges({ site_url: 'http://localhost:3000', uri_allow_list: '' }, { redirectUrls: [], siteUrl: 'http://localhost:5180' })
    assert.equal(replace.changes.site_url, 'http://localhost:5180')
  })

  it('turns sign-ups off only when asked and only when they are on', () => {
    assert.deepEqual(planAuthChanges({ disable_signup: false }, { redirectUrls: [], inviteOnly: true }).changes, { disable_signup: true })
    assert.deepEqual(planAuthChanges({ disable_signup: true }, { redirectUrls: [], inviteOnly: true }).changes, {})
  })

  it('changes nothing when everything is already in place', () => {
    const { changes } = planAuthChanges(
      { site_url: 'http://localhost:5180', uri_allow_list: redirectUrls.join(','), disable_signup: true },
      { redirectUrls, siteUrl: 'http://localhost:5180', inviteOnly: true },
    )
    assert.deepEqual(changes, {})
  })
})

describe('supabaseAdmin', () => {
  it('creates a confirmed account with the secret key and says why Supabase refused one', async (t) => {
    const calls = []
    t.mock.method(globalThis, 'fetch', async (url, init) => {
      calls.push({ url, init })
      return calls.length === 1
        ? new Response(JSON.stringify({ id: 'u1', email: 'sam@example.com' }), { status: 200 })
        : new Response(JSON.stringify({ msg: 'A user with this email address has already been registered' }), { status: 422 })
    })
    const admin = supabaseAdmin('https://abc.supabase.co/', 'sb_secret_x')

    assert.equal((await admin.create('sam@example.com', 'hunter22!', 'Sam Lee')).id, 'u1')
    assert.equal(calls[0].url, 'https://abc.supabase.co/auth/v1/admin/users')
    assert.equal(calls[0].init.headers.Authorization, 'Bearer sb_secret_x')
    assert.deepEqual(JSON.parse(calls[0].init.body), {
      email: 'sam@example.com', password: 'hunter22!', email_confirm: true, user_metadata: { full_name: 'Sam Lee' },
    })
    await assert.rejects(admin.create('sam@example.com', 'x', 'Sam'), /already been registered/)
  })
})
