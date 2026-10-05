import brand from '../brand.json'

export const DEFAULT_HIGHLIGHTS = ['Secure sign-in', 'Password reset by email', 'Accounts by invitation']

export function monogram(name) {
  const first = [...String(name ?? '').trim()][0]
  return first ? first.toUpperCase() : '•'
}

export default {
  name: brand.name || 'Your App',
  headline: brand.headline || 'Everything your team needs,\none sign-in away.',
  blurb: brand.blurb || '',
  highlights: Array.isArray(brand.highlights) ? brand.highlights : DEFAULT_HIGHLIGHTS,
  footer: brand.footer || '',
}
