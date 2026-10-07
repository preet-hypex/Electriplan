import type { PlanSource } from '../../model/types'
import { apiUrl } from '../../api/client'

/**
 * The untouched upload, positioned so its pixels line up with the plan's
 * millimetre coordinates.
 *
 * Model space has its origin at the top-left of the detected plan region, so
 * the full image starts at negative coordinates by exactly the crop offset.
 * Nothing here writes to the FloorPlan.
 */
export function BackgroundLayer({ source, opacity }: { source: PlanSource; opacity: number }) {
  const { mmPerPx, planRegion, imageWidth, imageHeight, imageUrl } = source
  return (
    <image
      href={apiUrl(imageUrl)}
      x={-planRegion.x * mmPerPx}
      y={-planRegion.y * mmPerPx}
      width={imageWidth * mmPerPx}
      height={imageHeight * mmPerPx}
      opacity={opacity}
      pointerEvents="none"
      preserveAspectRatio="none"
    />
  )
}

/** Faint reference grid, in metres. */
export function GridLayer({
  bounds,
  step = 1000,
  mmPerPixel,
}: {
  bounds: { minX: number; minY: number; maxX: number; maxY: number }
  step?: number
  mmPerPixel: number
}) {
  const lines: React.ReactNode[] = []
  const x0 = Math.floor(bounds.minX / step) * step
  const y0 = Math.floor(bounds.minY / step) * step
  for (let x = x0; x <= bounds.maxX; x += step) {
    lines.push(
      <line key={`x${x}`} x1={x} y1={bounds.minY} x2={x} y2={bounds.maxY} stroke="#cbd5e1" strokeWidth={mmPerPixel} />,
    )
  }
  for (let y = y0; y <= bounds.maxY; y += step) {
    lines.push(
      <line key={`y${y}`} x1={bounds.minX} y1={y} x2={bounds.maxX} y2={y} stroke="#cbd5e1" strokeWidth={mmPerPixel} />,
    )
  }
  return (
    <g pointerEvents="none" opacity={0.6}>
      {lines}
    </g>
  )
}
