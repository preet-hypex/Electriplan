import { execFileSync } from 'node:child_process'
import { userInfo } from 'node:os'

const ACCOUNT = process.env.USER || userInfo().username

function hasSecurityTool() {
  try {
    execFileSync('security', ['help'], { stdio: 'ignore' })
    return true
  } catch (e) {
    return e.code !== 'ENOENT'
  }
}

export const keychainAvailable =
  process.platform === 'darwin' && !process.env.SETUP_NO_KEYCHAIN && hasSecurityTool()

export function keychain(service, label) {
  const name = key => `${service}:${key}`

  return {
    get(key) {
      if (!keychainAvailable) return null
      try {
        return execFileSync('security', ['find-generic-password', '-a', ACCOUNT, '-s', name(key), '-w'], {
          stdio: ['ignore', 'pipe', 'ignore'],
        }).toString().replace(/\n$/, '')
      } catch {
        return null
      }
    },
    set(key, value) {
      if (!keychainAvailable) return false
      execFileSync('security', [
        'add-generic-password', '-U',
        '-a', ACCOUNT,
        '-s', name(key),
        '-D', `${label} dev environment`,
        '-j', 'Written by scripts/setup.mjs. Rebuilds .env and .env.server with npm run setup -- sync.',
        '-T', '/usr/bin/security',
        '-w', value,
      ], { stdio: 'ignore' })
      return true
    },
    remove(key) {
      if (!keychainAvailable) return false
      try {
        execFileSync('security', ['delete-generic-password', '-a', ACCOUNT, '-s', name(key)], { stdio: 'ignore' })
        return true
      } catch {
        return false
      }
    },
    describe: () => `${service}:<KEY> for account ${ACCOUNT}`,
  }
}
