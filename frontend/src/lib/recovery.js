const KEY = 'auth.passwordRecovery'
export const RECOVERY_LASTS_MS = 60 * 60 * 1000

export function readRecoverySubject(href) {
  try {
    const params = new URLSearchParams(new URL(href).hash.replace(/^#/, ''))
    const token = params.get('access_token')
    if (params.get('type') !== 'recovery' || !token) return null
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.sub === 'string' ? payload.sub : null
  } catch {
    return null
  }
}

export function sessionStore() {
  try {
    const storage = window.sessionStorage
    storage.getItem(KEY)
    return storage
  } catch {
    return null
  }
}

export function rememberRecovery(storage, subject, now = Date.now()) {
  try {
    storage?.setItem(KEY, JSON.stringify({ sub: subject, at: now }))
  } catch {
  }
}

export function isRecovering(storage, userId, now = Date.now()) {
  if (!userId) return false
  try {
    const recorded = JSON.parse(storage?.getItem(KEY) ?? 'null')
    return Boolean(recorded && recorded.sub === userId && now - recorded.at < RECOVERY_LASTS_MS)
  } catch {
    return false
  }
}

export function forgetRecovery(storage) {
  try {
    storage?.removeItem(KEY)
  } catch {
  }
}
