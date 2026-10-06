export const PRESETS = [
  { name: 'Blue', hex: '#2563EB' },
  { name: 'Indigo', hex: '#4F46E5' },
  { name: 'Violet', hex: '#7C3AED' },
  { name: 'Teal', hex: '#0F766E' },
  { name: 'Emerald', hex: '#047857' },
  { name: 'Rose', hex: '#E11D48' },
  { name: 'Orange', hex: '#C2410C' },
  { name: 'Graphite', hex: '#334155' },
]

const WHITE = { r: 255, g: 255, b: 255 }

export function parseHex(value) {
  const match = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(String(value ?? '').trim())
  if (!match) return null
  const digits = match[1].length === 3 ? [...match[1]].map(d => d + d).join('') : match[1]
  const n = parseInt(digits, 16)
  return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 }
}

export function toHex({ r, g, b }) {
  return '#' + [r, g, b].map(c => Math.round(c).toString(16).padStart(2, '0')).join('').toUpperCase()
}

export function rgbToHsl({ r, g, b }) {
  const [rn, gn, bn] = [r / 255, g / 255, b / 255]
  const max = Math.max(rn, gn, bn)
  const min = Math.min(rn, gn, bn)
  const l = (max + min) / 2
  const d = max - min
  if (d === 0) return { h: 0, s: 0, l: l * 100 }
  const s = d / (1 - Math.abs(2 * l - 1))
  let h
  if (max === rn) h = ((gn - bn) / d) % 6
  else if (max === gn) h = (bn - rn) / d + 2
  else h = (rn - gn) / d + 4
  return { h: (h * 60 + 360) % 360, s: s * 100, l: l * 100 }
}

export function hslToRgb({ h, s, l }) {
  const sn = clamp(s, 0, 100) / 100
  const ln = clamp(l, 0, 100) / 100
  const c = (1 - Math.abs(2 * ln - 1)) * sn
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1))
  const m = ln - c / 2
  const [r, g, b] =
    h < 60 ? [c, x, 0] :
    h < 120 ? [x, c, 0] :
    h < 180 ? [0, c, x] :
    h < 240 ? [0, x, c] :
    h < 300 ? [x, 0, c] : [c, 0, x]
  return { r: (r + m) * 255, g: (g + m) * 255, b: (b + m) * 255 }
}

export function luminance({ r, g, b }) {
  const [lr, lg, lb] = [r, g, b].map(c => {
    const v = c / 255
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * lr + 0.7152 * lg + 0.0722 * lb
}

export function contrast(a, b) {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

function clamp(n, lo, hi) {
  return Math.min(hi, Math.max(lo, n))
}

function hsl(h, s, l) {
  return toHex(hslToRgb({ h, s, l }))
}

function darkenUntil(base, against, ratio) {
  const { h, s, l } = rgbToHsl(base)
  for (let next = l; next >= 0; next -= 1) {
    const rgb = hslToRgb({ h, s, l: next })
    if (contrast(rgb, against) >= ratio) return toHex(rgb)
  }
  return '#000000'
}

function lightenUntil(base, against, ratio) {
  const { h, s, l } = rgbToHsl(base)
  for (let next = l; next <= 100; next += 1) {
    const rgb = hslToRgb({ h, s, l: next })
    if (contrast(rgb, against) >= ratio) return toHex(rgb)
  }
  return '#FFFFFF'
}

export function paletteFor(accentHex) {
  const accent = parseHex(accentHex)
  if (!accent) throw new Error(`Not a hex colour: ${accentHex}`)
  const { h, s, l } = rgbToHsl(accent)

  const ink = hslToRgb({ h, s: Math.min(s * 0.8, 70), l: 16 })
  const darkText = hslToRgb({ h, s: Math.min(s * 0.5, 40), l: 10 })
  const whiteReads = contrast(accent, WHITE) >= 4.5
  const onAccent = whiteReads || contrast(accent, WHITE) >= contrast(accent, darkText) ? WHITE : darkText

  return {
    '--accent': toHex(accent),
    '--accent-h': hsl(h, s, onAccent === WHITE ? l - 8 : l + 6),
    '--accent-text': darkenUntil(accent, WHITE, 4.5),
    '--accent-deep': hsl(h, s, Math.min(l, 35)),
    '--accent-t': hsl(h, Math.min(100, s * 1.25), 96),
    '--accent-b': hsl(h, Math.min(90, s * 1.07), 88),
    '--accent-soft': hsl(h, Math.min(85, s), 97.5),
    '--accent-glow': `rgba(${accent.r}, ${accent.g}, ${accent.b}, .45)`,
    '--accent-on-ink': lightenUntil(accent, ink, 3),
    '--on-accent': toHex(onAccent),
    '--ink': toHex(ink),
    '--side-text': hsl(h, Math.min(s, 40), 79),
    '--side-strong': hsl(h, Math.min(s, 45), 89),
    '--side-faint': hsl(h, Math.min(s, 25), 60),
  }
}

export function themeCss(accentHex) {
  const lines = Object.entries(paletteFor(accentHex)).map(([k, v]) => `  ${k}: ${v};`)
  return `:root {\n${lines.join('\n')}\n}\n`
}

export function readableOnWhite(accentHex) {
  const accent = parseHex(accentHex)
  return Boolean(accent) && contrast(accent, WHITE) >= 4.5
}
