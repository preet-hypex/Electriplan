import { describe, expect, it } from 'vitest'
import { RECOVERY_LASTS_MS, forgetRecovery, isRecovering, readRecoverySubject, rememberRecovery } from './recovery'

const jwt = payload => `x.${btoa(JSON.stringify(payload)).replace(/=+$/, '')}.y`

describe('readRecoverySubject', () => {
  it('reads the user id from a recovery link', () => {
    expect(readRecoverySubject(`http://x/reset-password#access_token=${jwt({ sub: 'u1' })}&type=recovery`)).toBe('u1')
  })

  it('ignores links that are not for recovery', () => {
    expect(readRecoverySubject(`http://x/#access_token=${jwt({ sub: 'u1' })}&type=invite`)).toBeNull()
    expect(readRecoverySubject('http://x/reset-password#type=recovery')).toBeNull()
    expect(readRecoverySubject('not a url')).toBeNull()
  })
})

describe('isRecovering', () => {
  it('holds for the same user within the hour, then lapses', () => {
    rememberRecovery(sessionStorage, 'u1', 1000)
    expect(isRecovering(sessionStorage, 'u1', 1000 + RECOVERY_LASTS_MS - 1)).toBe(true)
    expect(isRecovering(sessionStorage, 'u2', 1000)).toBe(false)
    expect(isRecovering(sessionStorage, 'u1', 1000 + RECOVERY_LASTS_MS)).toBe(false)
  })

  it('stops once forgotten', () => {
    rememberRecovery(sessionStorage, 'u1')
    forgetRecovery(sessionStorage)
    expect(isRecovering(sessionStorage, 'u1')).toBe(false)
  })
})
