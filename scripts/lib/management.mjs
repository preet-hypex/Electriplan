const API = (process.env.SUPABASE_MANAGEMENT_API || 'https://api.supabase.com').replace(/\/$/, '')

export const TOKEN_PAGE = 'https://supabase.com/dashboard/account/tokens'
export const apiKeysPage = ref => `https://supabase.com/dashboard/project/${ref || '_'}/settings/api-keys`
export const urlConfigPage = ref => `https://supabase.com/dashboard/project/${ref || '_'}/auth/url-configuration`

export class ManagementError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

export function management(token) {
  async function call(method, path, body) {
    const res = await fetch(`${API}${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'application/json',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    })
    const text = await res.text()
    if (!res.ok) {
      let message = text
      try { message = JSON.parse(text).message || text } catch {}
      throw new ManagementError(res.status, `${method} ${path} failed (${res.status}): ${message || res.statusText}`)
    }
    return text ? JSON.parse(text) : null
  }

  return {
    projects: () => call('GET', '/v1/projects'),
    apiKeys: ref => call('GET', `/v1/projects/${ref}/api-keys?reveal=true`),
    authConfig: ref => call('GET', `/v1/projects/${ref}/config/auth`),
    updateAuthConfig: (ref, changes) => call('PATCH', `/v1/projects/${ref}/config/auth`, changes),
  }
}

export const projectRef = project => project.ref || project.id

export function pickKeys(keys) {
  const usable = (keys ?? []).filter(k => typeof k.api_key === 'string' && k.api_key.length > 0)
  return {
    publishable: usable.find(k => k.type === 'publishable' && /^sb_publishable_[A-Za-z0-9_-]+$/.test(k.api_key))?.api_key ?? null,
    secret: usable.find(k => k.type === 'secret' && /^sb_secret_[A-Za-z0-9_-]+$/.test(k.api_key))?.api_key ?? null,
  }
}

export function splitAllowList(value) {
  return String(value ?? '').split(',').map(s => s.trim()).filter(Boolean)
}

export function planAuthChanges(current, { redirectUrls, siteUrl, inviteOnly }) {
  const changes = {}
  const existing = splitAllowList(current.uri_allow_list)
  const added = redirectUrls.filter(u => !existing.includes(u))
  if (added.length) changes.uri_allow_list = [...existing, ...added].join(',')

  const placeholder = !current.site_url || /^http:\/\/localhost:3000\/?$/.test(current.site_url)
  if (siteUrl && placeholder && current.site_url !== siteUrl) changes.site_url = siteUrl

  if (inviteOnly && !current.disable_signup) changes.disable_signup = true

  return { changes, added }
}
