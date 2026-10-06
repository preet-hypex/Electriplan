import { describe, expect, it } from 'vitest'
import { PRESETS, contrast, paletteFor, parseHex, readableOnWhite, themeCss } from './palette'

const WHITE = { r: 255, g: 255, b: 255 }

describe('parseHex', () => {
  it('reads six and three digit colours, with or without the hash', () => {
    expect(parseHex('#2563EB')).toEqual({ r: 37, g: 99, b: 235 })
    expect(parseHex('2563eb')).toEqual({ r: 37, g: 99, b: 235 })
    expect(parseHex('#fa0')).toEqual({ r: 255, g: 170, b: 0 })
  })

  it('rejects anything else', () => {
    for (const bad of ['', 'blue', '#12345', '#GGGGGG', null, undefined]) expect(parseHex(bad)).toBeNull()
  })
})

describe('paletteFor', () => {
  it('keeps the chosen colour as the accent', () => {
    expect(paletteFor('#7c3aed')['--accent']).toBe('#7C3AED')
  })

  it('gives every preset white button text that passes WCAG AA', () => {
    for (const preset of PRESETS) {
      expect(readableOnWhite(preset.hex), preset.name).toBe(true)
      expect(paletteFor(preset.hex)['--on-accent'], preset.name).toBe('#FFFFFF')
    }
  })

  it('switches to dark button text when white would not read on a light accent', () => {
    const p = paletteFor('#FACC15')
    expect(p['--on-accent']).not.toBe('#FFFFFF')
    expect(contrast(parseHex(p['--on-accent']), parseHex('#FACC15'))).toBeGreaterThanOrEqual(4.5)
  })

  it('darkens link colour until it reads on white, whatever the accent', () => {
    for (const hex of ['#FACC15', '#22D3EE', '#A3E635', '#2563EB']) {
      expect(contrast(parseHex(paletteFor(hex)['--accent-text']), WHITE), hex).toBeGreaterThanOrEqual(4.5)
    }
  })

  it('keeps every piece of side panel text readable on the generated ink', () => {
    for (const hex of ['#FACC15', '#2563EB', '#334155', '#E11D48', '#A3E635', ...PRESETS.map(p => p.hex)]) {
      const p = paletteFor(hex)
      const ink = parseHex(p['--ink'])
      expect(contrast(ink, WHITE), `${hex} heading`).toBeGreaterThanOrEqual(7)
      expect(contrast(ink, parseHex(p['--side-text'])), `${hex} body`).toBeGreaterThanOrEqual(7)
      expect(contrast(ink, parseHex(p['--side-strong'])), `${hex} pills`).toBeGreaterThanOrEqual(7)
      expect(contrast(ink, parseHex(p['--side-faint'])), `${hex} footer`).toBeGreaterThanOrEqual(4.5)
      expect(contrast(ink, parseHex(p['--accent-on-ink'])), `${hex} dots`).toBeGreaterThanOrEqual(3)
    }
  })

  it('throws on a value that is not a colour', () => {
    expect(() => paletteFor('teal')).toThrow(/Not a hex colour/)
  })
})

describe('themeCss', () => {
  it('writes every token into a :root block', () => {
    const css = themeCss('#0F766E')
    expect(css).toMatch(/^:root \{\n/)
    for (const key of Object.keys(paletteFor('#0F766E'))) expect(css).toContain(`  ${key}: `)
  })
})
