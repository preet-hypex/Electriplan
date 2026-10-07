import { useRef, useState } from 'react'
import { useEditor } from '../state/store'
import { parseFloorPlanJson, serialiseFloorPlan } from '../model/serialise'
import { exportPlan } from '../api/client'
import { SCALE_CONFIDENCE_FLOOR } from '../model/metrics'
import { Icon, type IconName } from './icons'

function download(filename: string, text: string) {
  const blob = new Blob([text], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

/** A plan name as a file name: kept readable, stripped of what file systems dislike. */
export function fileNameFor(planName: string): string {
  const base = planName.trim().replace(/[\\/:*?"<>|]+/g, '-').replace(/\s+/g, ' ') || 'floor-plan'
  return `${base}.json`
}

function Chip({ tone, children, title }: { tone: 'ok' | 'warn' | 'muted'; children: React.ReactNode; title?: string }) {
  const tones = {
    ok: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    warn: 'border-amber-200 bg-amber-50 text-amber-800',
    muted: 'border-slate-200 bg-slate-50 text-slate-600',
  }
  return (
    <span title={title} className={`inline-flex h-6 items-center gap-1.5 rounded-full border px-2.5 text-xs font-medium ${tones[tone]}`}>
      {children}
    </span>
  )
}

function CommandButton({
  icon,
  label,
  onClick,
  disabled,
  title,
}: {
  icon: IconName
  label?: string
  onClick: () => void
  disabled?: boolean
  title?: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title ?? label}
      aria-label={label ?? title}
      className="inline-flex h-8 items-center gap-1.5 rounded border border-slate-200 bg-white px-2.5 text-[13px] font-medium text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-300"
    >
      <Icon name={icon} />
      {label}
    </button>
  )
}

/**
 * The plan as a document: its name, its state, and the commands that act on
 * the whole of it (open, analyse, calibrate, undo, save).
 */
export function DocumentHeader({ onNewAnalysis, onCalibrate }: { onNewAnalysis: () => void; onCalibrate: () => void }) {
  const plan = useEditor((s) => s.plan)
  const loadPlan = useEditor((s) => s.loadPlan)
  const planName = useEditor((s) => s.planName)
  const setPlanName = useEditor((s) => s.setPlanName)
  const markSaved = useEditor((s) => s.markSaved)
  const undo = useEditor((s) => s.undo)
  const redo = useEditor((s) => s.redo)
  const past = useEditor((s) => s.past.length)
  const future = useEditor((s) => s.future.length)
  const dirty = useEditor((s) => s.dirty)
  const fileRef = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const openJson = async (file: File) => {
    try {
      loadPlan(parseFloorPlanJson(await file.text()), file.name.replace(/\.json$/i, ''))
      setError(null)
    } catch (e) {
      setError(`${file.name} could not be opened: ${(e as Error).message}`)
    }
  }

  const save = async () => {
    setBusy(true)
    setError(null)
    try {
      // Prefer the analyser's canonical serialisation; fall back to local JSON
      // so the editor still saves when the analyser is not running.
      let text: string
      try {
        text = await exportPlan(plan)
      } catch {
        text = serialiseFloorPlan(plan)
      }
      download(fileNameFor(planName), text)
      markSaved()
    } finally {
      setBusy(false)
    }
  }

  const source = plan.source
  const scaleOk = source ? source.scaleMethod !== 'fallback' && source.scaleConfidence >= SCALE_CONFIDENCE_FLOOR : false

  return (
    <header className="flex shrink-0 flex-wrap items-center gap-x-4 gap-y-2 border-b border-slate-200 bg-white px-4 py-2.5">
      <div className="flex min-w-0 items-center gap-3">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-100 text-blue-600">
          <Icon name="room" size={18} />
        </span>
        <div className="min-w-0">
          <p className="text-[11px] font-medium uppercase tracking-wide text-slate-500">Floor plan</p>
          <label className="group flex items-center gap-1.5">
            <input
              value={planName}
              onChange={(e) => setPlanName(e.target.value)}
              onBlur={(e) => !e.target.value.trim() && setPlanName('Untitled plan')}
              aria-label="Plan name"
              size={Math.max(12, planName.length)}
              className="-ml-1 rounded border border-transparent bg-transparent px-1 text-[15px] font-semibold text-slate-900 hover:border-slate-200 focus:border-blue-500 focus:outline-none"
            />
            <Icon name="pencil" size={13} className="text-slate-400 opacity-0 group-hover:opacity-100" />
          </label>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {dirty ? <Chip tone="warn">● Unsaved changes</Chip> : <Chip tone="ok">✓ Saved</Chip>}
        {source ? (
          <Chip
            tone={scaleOk ? 'muted' : 'warn'}
            title={`Scale found by: ${source.scaleMethod}`}
          >
            <Icon name="ruler" size={13} />
            {source.mmPerPx.toFixed(2)} mm/px · {Math.round(source.scaleConfidence * 100)}%
          </Chip>
        ) : (
          <Chip tone="muted">Drawn by hand</Chip>
        )}
        {source && (
          <Chip tone="muted" title="The uploaded image, shown behind the plan">
            <Icon name="image" size={13} />
            {source.imageWidth}×{source.imageHeight} px
          </Chip>
        )}
      </div>

      {error && <p className="text-xs text-red-600">{error}</p>}

      <div className="ml-auto flex items-center gap-2">
        <div className="flex items-center gap-1">
          <CommandButton icon="undo" title="Undo (⌘Z)" onClick={undo} disabled={past === 0} />
          <CommandButton icon="redo" title="Redo (⌘⇧Z)" onClick={redo} disabled={future === 0} />
        </div>
        <span className="h-6 w-px bg-slate-200" />
        <input
          ref={fileRef}
          type="file"
          accept="application/json,.json"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0]
            if (f) void openJson(f)
            e.target.value = ''
          }}
        />
        <CommandButton icon="open" label="Open" title="Open a plan saved as JSON" onClick={() => fileRef.current?.click()} />
        <CommandButton icon="scan" label="Analyse image" onClick={onNewAnalysis} />
        <CommandButton icon="ruler" label="Calibrate" title="Set the scale from a length you know" onClick={onCalibrate} />
        <button
          type="button"
          onClick={save}
          disabled={busy}
          className="inline-flex h-8 items-center gap-1.5 rounded bg-blue-600 px-3.5 text-[13px] font-medium text-white hover:bg-blue-700 disabled:opacity-60"
        >
          <Icon name="save" />
          {busy ? 'Saving…' : 'Save'}
        </button>
      </div>
    </header>
  )
}
