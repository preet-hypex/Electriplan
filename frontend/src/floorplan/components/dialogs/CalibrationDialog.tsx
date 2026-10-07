import { useMemo, useState } from 'react'
import { useEditor } from '../../state/store'
import { calibrate } from '../../api/client'
import { polygonBounds } from '../../geometry/polygon'
import { wallLength } from '../../geometry/wall'

interface Candidate {
  id: string
  label: string
  /** Current length in mm under the plan's present scale. */
  millimetres: number
}

/**
 * Rescale the whole model about its origin. The source image is untouched; only
 * the mm-per-pixel figure that produced these coordinates changes with it.
 */
function applyFactor(factor: number) {
  useEditor.getState().commit((draft) => {
    const scalePoint = (p: { x: number; y: number }) => {
      p.x *= factor
      p.y *= factor
    }
    for (const w of draft.walls) {
      scalePoint(w.start)
      scalePoint(w.end)
      w.thickness *= factor
    }
    for (const r of draft.rooms) {
      r.polygon.forEach(scalePoint)
      scalePoint(r.labelPosition)
    }
    for (const l of draft.labels) scalePoint(l.position)
    for (const d of draft.dimensions) {
      scalePoint(d.start)
      scalePoint(d.end)
    }
    for (const d of draft.doors) {
      d.position *= factor
      d.width *= factor
    }
    for (const w of draft.windows) {
      w.position *= factor
      w.width *= factor
    }
    if (draft.source) {
      draft.source.mmPerPx *= factor
      draft.source.scaleConfidence = 1
      draft.source.scaleMethod = 'manual'
    }
  })
}

export function CalibrationDialog({ onClose }: { onClose: () => void }) {
  const plan = useEditor((s) => s.plan)
  const selection = useEditor((s) => s.selection)

  const candidates = useMemo<Candidate[]>(() => {
    const list: Candidate[] = []
    if (selection?.kind === 'wall') {
      const w = plan.walls.find((x) => x.id === selection.id)
      if (w) list.push({ id: `wall:${w.id}`, label: `Selected wall length`, millimetres: wallLength(w) })
    }
    for (const room of plan.rooms) {
      if (room.polygon.length < 3) continue
      const b = polygonBounds(room.polygon)
      const name = room.name || room.id
      list.push({ id: `${room.id}:w`, label: `${name} — width`, millimetres: b.maxX - b.minX })
      list.push({ id: `${room.id}:h`, label: `${name} — height`, millimetres: b.maxY - b.minY })
    }
    return list
  }, [plan, selection])

  const [choice, setChoice] = useState(candidates[0]?.id ?? '')
  const selected = candidates.find((c) => c.id === choice)
  const [actual, setActual] = useState<string>(selected ? String(Math.round(selected.millimetres)) : '')
  const [busy, setBusy] = useState(false)
  const [note, setNote] = useState<string | null>(null)

  const mmPerPx = plan.source?.mmPerPx
  const detectedPx = selected && mmPerPx ? selected.millimetres / mmPerPx : null

  const apply = async () => {
    const target = Number(actual)
    if (!selected || !Number.isFinite(target) || target <= 0) {
      setNote('Enter the real-world length in millimetres.')
      return
    }
    setBusy(true)
    setNote(null)
    try {
      let factor = target / selected.millimetres
      if (detectedPx && detectedPx > 0) {
        // Let the backend own the arithmetic when it is available.
        try {
          const res = await calibrate({
            pixels: detectedPx,
            millimetres: target,
            currentMmPerPx: mmPerPx,
          })
          if (mmPerPx) factor = res.mmPerPx / mmPerPx
        } catch {
          /* offline: the local ratio above is identical arithmetic */
        }
      }
      applyFactor(factor)
      onClose()
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-20 flex items-center justify-center bg-[color-mix(in_srgb,var(--ink)_40%,transparent)] p-4">
      <div className="w-full max-w-md rounded-lg bg-white p-5 shadow-xl">
        <h2 className="text-base font-semibold text-slate-900">Scale calibration</h2>
        <p className="mt-1 text-xs text-slate-500">
          Pick something whose real size you know. Everything in the plan is rescaled by the same
          factor.
        </p>

        {candidates.length === 0 ? (
          <p className="mt-4 text-sm text-slate-600">
            There is nothing to measure yet. Detect or draw some geometry first.
          </p>
        ) : (
          <div className="mt-4 space-y-3">
            <label className="block">
              <span className="mb-1 block text-[11px] font-medium uppercase tracking-wide text-slate-500">
                Select a known dimension
              </span>
              <select
                className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
                value={choice}
                onChange={(e) => {
                  setChoice(e.target.value)
                  const c = candidates.find((x) => x.id === e.target.value)
                  if (c) setActual(String(Math.round(c.millimetres)))
                }}
              >
                {candidates.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.label}
                  </option>
                ))}
              </select>
            </label>

            <dl className="grid grid-cols-2 gap-y-1 rounded bg-slate-50 p-2 text-xs text-slate-600">
              <dt className="text-slate-400">Currently</dt>
              <dd className="text-right tabular-nums">{Math.round(selected?.millimetres ?? 0)} mm</dd>
              {detectedPx !== null && (
                <>
                  <dt className="text-slate-400">Detected</dt>
                  <dd className="text-right tabular-nums">{Math.round(detectedPx)} px</dd>
                </>
              )}
            </dl>

            <label className="block">
              <span className="mb-1 block text-[11px] font-medium uppercase tracking-wide text-slate-500">
                Actual (mm)
              </span>
              <input
                className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm tabular-nums"
                inputMode="numeric"
                value={actual}
                onChange={(e) => setActual(e.target.value)}
              />
            </label>

            {note && <p className="text-xs text-red-600">{note}</p>}
          </div>
        )}

        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={apply}
            disabled={busy || candidates.length === 0}
            className="rounded bg-blue-600 px-3 py-1.5 text-sm text-white hover:bg-blue-700 disabled:bg-slate-300"
          >
            Apply scale
          </button>
        </div>
      </div>
    </div>
  )
}
