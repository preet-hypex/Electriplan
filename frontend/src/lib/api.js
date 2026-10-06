import { supabase } from './supabase'

const BASE = import.meta.env.VITE_API_URL || ''

const FALLBACK_MESSAGES = {
  400: 'That was not accepted. Check what you entered and try again.',
  401: 'Your session has ended. Sign in again.',
  403: 'You do not have access to do that.',
  404: 'That could not be found.',
  409: 'That clashes with a change someone else made. Refresh and try again.',
  429: 'Too many attempts. Wait a moment and try again.',
  502: 'A service the app depends on did not answer. Try again in a moment.',
}

export async function api(path, options = {}) {
  const { json, headers, ...rest } = options
  const { data } = await supabase.auth.getSession()
  const token = data.session?.access_token

  const res = await fetch(`${BASE}${path}`, {
    ...rest,
    ...(json === undefined ? {} : { body: JSON.stringify(json) }),
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(headers || {}),
    },
  })

  const type = res.headers.get('content-type') ?? ''
  const body = res.status === 204 ? null : type.includes('json') ? await res.json() : await res.text()

  if (!res.ok) {
    const err = new Error(body?.message || FALLBACK_MESSAGES[res.status] || `Something went wrong (${res.status}). Try again.`)
    err.status = res.status
    err.body = body
    throw err
  }
  return body
}
