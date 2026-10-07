import type { Detected } from './types'

export type ConfidenceBand = 'high' | 'medium' | 'low' | 'unknown'

/**
 * Bands from the spec:
 *   0.85–1.00 high, 0.60–0.84 medium, below 0.60 low.
 * Manually created or edited objects have no confidence and are 'unknown',
 * which is rendered the same as high — the user is the authority.
 */
export function bandOf(obj: Detected | undefined): ConfidenceBand {
  const c = obj?.confidence
  if (c === undefined || c === null) return 'unknown'
  if (c >= 0.85) return 'high'
  if (c >= 0.6) return 'medium'
  return 'low'
}

export function isLowConfidence(obj: Detected | undefined): boolean {
  return bandOf(obj) === 'low'
}

export function bandColour(band: ConfidenceBand): string {
  switch (band) {
    case 'low':
      return '#dc2626'
    case 'medium':
      return '#d97706'
    default:
      return '#16a34a'
  }
}

export function formatConfidence(obj: Detected | undefined): string {
  if (obj?.confidence === undefined) return 'manual'
  return `${Math.round(obj.confidence * 100)}%`
}
