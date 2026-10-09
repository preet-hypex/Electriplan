import { useMemo } from 'react'
import type { FloorPlan, Point } from '../model/types'
import { boundsOfPoints, growBounds } from '../geometry/bounds'

const ROOM_FILLS = ['#E8F0FE', '#E7F6EE', '#FFF5E1', '#F3E8FF', '#FDEDEC', '#E6F7F8']

/**
 * A plan drawn small and read-only: rooms (with their names) and walls, to
 * recognise a version before restoring it. Not the editor: nothing here can
 * be selected or changed.
 */
export function PlanPreview({ plan, label }: { plan: FloorPlan; label: string }) {
  const view = useMemo(() => {
    const points: Point[] = [
      ...plan.walls.flatMap((w) => [w.start, w.end]),
      ...plan.rooms.flatMap((r) => r.polygon),
    ]
    const b = boundsOfPoints(points)
    if (!b) return null
    const margin = Math.max(b.maxX - b.minX, b.maxY - b.minY) * 0.04 + 200
    return growBounds(b, margin)
  }, [plan])

  if (!view) {
    return (
      <div className="flex h-56 items-center justify-center rounded border border-dashed border-slate-300 text-sm text-slate-500">
        This version has nothing drawn yet.
      </div>
    )
  }

  const width = view.maxX - view.minX
  const height = view.maxY - view.minY
  const text = Math.max(width, height) / 45

  return (
    <svg
      role="img"
      aria-label={label}
      viewBox={`${view.minX} ${view.minY} ${width} ${height}`}
      className="h-64 w-full rounded border border-slate-200 bg-white"
      preserveAspectRatio="xMidYMid meet"
    >
      {plan.rooms.map((room, i) => (
        <polygon
          key={room.id}
          points={room.polygon.map((p) => `${p.x},${p.y}`).join(' ')}
          fill={room.colour ?? ROOM_FILLS[i % ROOM_FILLS.length]}
          stroke="none"
        />
      ))}
      {plan.walls.map((w) => (
        <line key={w.id} x1={w.start.x} y1={w.start.y} x2={w.end.x} y2={w.end.y}
          stroke="#1F2A44" strokeWidth={w.thickness} strokeLinecap="square" />
      ))}
      {plan.rooms.map((room) => (
        <text key={`${room.id}-name`} x={room.labelPosition.x} y={room.labelPosition.y} fontSize={text}
          textAnchor="middle" dominantBaseline="middle" fill="#3C4863" fontFamily="Inter, system-ui, sans-serif">
          {room.name}
        </text>
      ))}
    </svg>
  )
}
