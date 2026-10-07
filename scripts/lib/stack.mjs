import { spawnSync } from 'node:child_process'

export function psql(root, sql) {
  const args = ['compose', 'exec', '-T', 'db', 'psql', '-U', 'plannasaas', '-d', 'plannasaas', '-v', 'ON_ERROR_STOP=1', '-qtA']
  const run = spawnSync('docker', args, { cwd: root, input: sql, encoding: 'utf8' })
  return {
    ok: run.status === 0,
    out: (run.stdout ?? '').trim(),
    err: (run.stderr || run.error?.message || '').trim(),
  }
}

export function copiedUsers(root) {
  const result = psql(root, 'select count(*) from plannasaas.supabase_user;\n')
  return result.ok ? Number(result.out) : null
}

export function supabaseAdmin(url, secretKey) {
  async function call(method, path, body) {
    const res = await fetch(`${url.replace(/\/+$/, '')}${path}`, {
      method,
      headers: {
        apikey: secretKey,
        Authorization: `Bearer ${secretKey}`,
        'Content-Type': 'application/json',
      },
      body: body ? JSON.stringify(body) : undefined,
    })
    const text = await res.text()
    let json = null
    try { json = text ? JSON.parse(text) : null } catch {}
    if (!res.ok) throw new Error(json?.msg || json?.message || json?.error_description || `Supabase Auth answered ${res.status}`)
    return json
  }

  return {
    create(email, password, fullName) {
      return call('POST', '/auth/v1/admin/users', {
        email, password, email_confirm: true, user_metadata: { full_name: fullName },
      })
    },
  }
}
