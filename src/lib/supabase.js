import { createClient } from '@supabase/supabase-js'
import { readRecoverySubject, rememberRecovery, sessionStore } from './recovery'

const runtime = (typeof window !== 'undefined' && window.__APP_ENV__) || {}
const url = runtime.VITE_SUPABASE_URL || import.meta.env.VITE_SUPABASE_URL
const publishableKey = runtime.VITE_SUPABASE_PUBLISHABLE_KEY || import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY

export const configError = (() => {
  if (!url || !publishableKey) {
    return 'VITE_SUPABASE_URL and VITE_SUPABASE_PUBLISHABLE_KEY are not set. Run npm run setup to fetch them.'
  }
  if (!/^https:\/\/[a-z0-9-]+\.supabase\.co\/?$/.test(url)) {
    return `VITE_SUPABASE_URL does not look like a project URL: ${url}`
  }
  if (publishableKey.startsWith('sb_secret_')) {
    return 'VITE_SUPABASE_PUBLISHABLE_KEY holds a secret key. Rotate it in the Supabase dashboard now and put the sb_publishable_... key here instead.'
  }
  if (!publishableKey.startsWith('sb_publishable_')) {
    return 'VITE_SUPABASE_PUBLISHABLE_KEY is not a publishable key. Copy the sb_publishable_... key from Project settings -> API keys.'
  }
  return null
})()

if (configError) console.error(`Supabase config: ${configError}`)

export function readAuthRedirectError(href) {
  try {
    const url = new URL(href)
    for (const params of [new URLSearchParams(url.hash.replace(/^#/, '')), url.searchParams]) {
      const reason = params.get('error_description')
      if (reason) return reason
      if (params.get('error')) return 'That link could not be used'
    }
  } catch {
  }
  return null
}

export const authRedirectError = typeof window === 'undefined' ? null : readAuthRedirectError(window.location.href)

if (typeof window !== 'undefined') {
  const recovering = readRecoverySubject(window.location.href)
  if (recovering) rememberRecovery(sessionStore(), recovering)
}

export const supabase = createClient(
  configError ? 'https://placeholder.supabase.co' : url,
  configError ? 'sb_publishable_placeholder' : publishableKey,
  { auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true } },
)
