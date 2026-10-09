import { useCallback, useEffect, useRef, useState } from 'react'
import FloorPlanApp from './FloorPlanApp'
import { Chip } from './components/DocumentHeader'
import { Icon } from './components/icons'
import { useAutosave, type SaveState } from './hooks/useAutosave'
import { useEditor } from './state/store'
import { parseFloorPlanJson, serialiseFloorPlan } from './model/serialise'
import type { FloorPlan } from './model/types'
import {
  approveFloorPlan, floorPlanHistory, openFloorPlan, restoreFloorPlanVersion, saveFloorPlanDraft, saveFloorPlanVersion,
  uploadFloorPlanImage,
} from '../api/floorPlans'
import { STAGE_LABELS, houseStages } from '../api/projects'
import type { FloorPlanDocument, FloorPlanVersion, HouseStage, StageEvent } from '../api'

const EMPTY: FloorPlan = {
  version: 1, units: 'mm', walls: [], rooms: [], doors: [], windows: [], openings: [], labels: [], dimensions: [],
}

/** The plan as the API takes it: exactly what the editor would save to a file. */
function toDocument(plan: FloorPlan): FloorPlan {
  return JSON.parse(serialiseFloorPlan(plan)) as FloorPlan
}

function fromDocument(doc: FloorPlanDocument): FloorPlan {
  return parseFloorPlanJson(JSON.stringify(doc.document))
}

/**
 * The floor-plan editor working on one house: it opens the house's plan (or
 * the upload screen when there is none), saves every change as the house's
 * draft a moment after editing pauses, freezes versions on "Save version",
 * and restores earlier ones from the history.
 */
export default function HouseFloorPlanEditor({
  houseId,
  title,
  stage: initialStage,
  canEdit,
  getAccessToken,
}: {
  houseId: string
  title: string
  /** Where the house is when the page opens; every save, upload and approval says where it is after. */
  stage: HouseStage
  /** May save (floor-plan.edit, and the house is not archived). Otherwise the plan can be looked at, not saved. */
  canEdit: boolean
  getAccessToken: () => Promise<string | null>
}) {
  const [load, setLoad] = useState<{ state: 'loading' | 'ready' | 'error'; error?: string }>({ state: 'loading' })
  /** The draft's version from the last open or save; undefined when there is no draft. */
  const draftVersion = useRef<number | undefined>(undefined)
  const [hasDraft, setHasDraft] = useState(false)
  const [dialog, setDialog] = useState<'version' | 'approve' | 'history' | null>(null)
  const [stage, setStage] = useState<HouseStage>(initialStage)

  const save = useCallback(async (plan: FloorPlan) => {
    const doc = await saveFloorPlanDraft(houseId, toDocument(plan), draftVersion.current)
    draftVersion.current = doc.version
    setHasDraft(true)
    setStage(doc.houseStage)
  }, [houseId])

  const autosave = useAutosave({ save, enabled: canEdit && load.state === 'ready' })
  const { setSaved } = autosave

  /** Puts a plan from the server in the editor, as the plan already saved. */
  const show = useCallback((plan: FloorPlan, doc: FloorPlanDocument | null) => {
    useEditor.getState().loadPlan(plan, title)
    draftVersion.current = doc?.state === 'draft' ? doc.version : undefined
    setHasDraft(doc?.state === 'draft')
    if (doc) setStage(doc.houseStage)
    setSaved(useEditor.getState().plan)
  }, [title, setSaved])

  const open = useCallback(async () => {
    setLoad({ state: 'loading' })
    try {
      const doc = await openFloorPlan(houseId)
      show(fromDocument(doc), doc)
      setLoad({ state: 'ready' })
    } catch (e) {
      if ((e as { status?: number }).status === 404) {
        show(EMPTY, null) // no floor plan yet: the upload screen
        setLoad({ state: 'ready' })
      } else {
        setLoad({ state: 'error', error: (e as Error).message })
      }
    }
  }, [houseId, show])

  useEffect(() => {
    void open()
  }, [open])

  if (load.state === 'loading') return <div className="loading">Opening the floor plan…</div>
  if (load.state === 'error') {
    return (
      <div className="loading">
        <div className="callout bad" role="alert">
          <span>The floor plan could not be opened: {load.error}</span>
          <button type="button" className="btn" onClick={() => void open()}>Try again</button>
        </div>
      </div>
    )
  }

  /**
   * An uploaded image: the API keeps it and saves the analysed plan as the
   * draft, so that plan is already saved when the editor opens it.
   */
  const analyse = async (file: File): Promise<FloorPlan> => {
    if (canEdit) await autosave.flush()
    const doc = await uploadFloorPlanImage(houseId, file, draftVersion.current)
    draftVersion.current = doc.version
    setHasDraft(true)
    setStage(doc.houseStage)
    const plan = fromDocument(doc)
    setSaved(plan)
    return plan
  }

  const restore = async (versionNo: number) => {
    if (canEdit) await autosave.flush()
    const doc = await restoreFloorPlanVersion(houseId, versionNo, draftVersion.current)
    show(fromDocument(doc), doc)
    setDialog(null)
  }

  /** Saves the draft as a version (if there is one) and approves it: the house moves to "Floor plan approved". */
  const approve = async (note: string) => {
    await autosave.flush()
    const doc = await approveFloorPlan(houseId, draftVersion.current, note || undefined)
    draftVersion.current = undefined
    setHasDraft(false)
    setStage(doc.houseStage)
    setDialog(null)
  }

  const saveVersion = async (note: string) => {
    await autosave.flush()
    if (draftVersion.current === undefined) throw new Error('There are no changes to save as a version.')
    await saveFloorPlanVersion(houseId, draftVersion.current, note || undefined)
    draftVersion.current = undefined
    setHasDraft(false)
    setDialog(null)
  }

  return (
    <>
      <FloorPlanApp
        getAccessToken={getAccessToken}
        house={{
          title,
          analyse,
          status: (
            <>
              <StageChip stage={stage} />
              <SaveStatus state={autosave.state} canEdit={canEdit} onRetry={() => void autosave.flush().catch(() => {})} />
            </>
          ),
          banner: <Banner state={autosave.state} canEdit={canEdit} error={autosave.error} onReload={() => void open()} />,
          actions: (
            <>
              <button type="button" onClick={() => setDialog('history')} title="Earlier versions of this floor plan"
                className="inline-flex h-8 items-center gap-1.5 rounded border border-slate-200 bg-white px-2.5 text-[13px] font-medium text-slate-700 hover:bg-slate-50">
                <Icon name="open" />History
              </button>
              {canEdit && stage === 'floor_plan_review' && (
                <button type="button" onClick={() => setDialog('approve')}
                  title="Approve this floor plan: it is saved as a version and electrical design can start from it"
                  className="inline-flex h-8 items-center gap-1.5 rounded border border-emerald-300 bg-emerald-50 px-2.5 text-[13px] font-medium text-emerald-800 hover:bg-emerald-100">
                  <Icon name="check" />Approve floor plan
                </button>
              )}
              {canEdit && (
                <button type="button" onClick={() => setDialog('version')}
                  disabled={!hasDraft && autosave.state === 'saved'}
                  title={!hasDraft && autosave.state === 'saved' ? 'No changes since the last version' : 'Keep this plan as a numbered version'}
                  className="inline-flex h-8 items-center gap-1.5 rounded bg-blue-600 px-3.5 text-[13px] font-medium text-white hover:bg-blue-700 disabled:opacity-50">
                  <Icon name="save" />Save version
                </button>
              )}
            </>
          ),
        }}
      />
      {dialog === 'version' && <SaveVersionDialog onSave={saveVersion} onClose={() => setDialog(null)} />}
      {dialog === 'approve' && (
        <SaveVersionDialog
          title="Approve the floor plan"
          explanation="Approving saves the plan as a version and marks the house's floor plan as approved: electrical design starts from it. Changing the plan afterwards sends it back for checking."
          action="Approve"
          onSave={approve}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog === 'history' && (
        <HistoryDialog houseId={houseId} canRestore={canEdit} onRestore={restore} onClose={() => setDialog(null)} />
      )}
    </>
  )
}

function SaveStatus({ state, canEdit, onRetry }: { state: SaveState; canEdit: boolean; onRetry: () => void }) {
  if (!canEdit) return <Chip tone="muted">View only</Chip>
  switch (state) {
    case 'saved': return <Chip tone="ok">✓ All changes saved</Chip>
    case 'pending': return <Chip tone="warn">● Unsaved changes</Chip>
    case 'saving': return <Chip tone="muted">Saving…</Chip>
    case 'conflict': return <Chip tone="warn">Changed elsewhere</Chip>
    case 'failed':
      return (
        <button type="button" onClick={onRetry} title="Try saving again">
          <Chip tone="warn">Not saved · Retry</Chip>
        </button>
      )
  }
}

function Banner({ state, canEdit, error, onReload }: {
  state: SaveState; canEdit: boolean; error: string | null; onReload: () => void
}) {
  const bar = 'flex shrink-0 items-center gap-3 border-b px-4 py-2 text-[13px]'
  if (!canEdit) {
    return (
      <div className={`${bar} border-slate-200 bg-slate-50 text-slate-600`}>
        You can look at this plan, but changes are not saved: your role cannot edit floor plans, or the house is archived.
      </div>
    )
  }
  if (state === 'conflict') {
    return (
      <div className={`${bar} border-amber-200 bg-amber-50 text-amber-900`} role="alert">
        <span>Someone else saved this plan while you were editing. Reload to continue from theirs; download yours first to keep it.</span>
        <button type="button" onClick={onReload} className="ml-auto rounded border border-amber-300 bg-white px-2.5 py-1 font-medium">Reload</button>
      </div>
    )
  }
  if (state === 'failed') {
    return (
      <div className={`${bar} border-red-200 bg-red-50 text-red-800`} role="alert">
        Your latest changes are not saved: {error}. They are kept here; saving is retried on your next change.
      </div>
    )
  }
  return null
}

function Dialog({ title, children, onClose }: { title: string; children: React.ReactNode; onClose: () => void }) {
  useEffect(() => {
    const close = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [onClose])
  return (
    <div className="fp-root fixed inset-0 z-30 flex items-center justify-center bg-[color-mix(in_srgb,var(--ink)_40%,transparent)] p-4">
      <div role="dialog" aria-modal="true" aria-label={title} className="w-full max-w-lg rounded-lg bg-white p-5 shadow-xl">
        <h2 className="text-base font-semibold text-slate-900">{title}</h2>
        {children}
      </div>
    </div>
  )
}

function SaveVersionDialog({
  onSave,
  onClose,
  title = 'Save a version',
  explanation = 'A version is a frozen copy you can always come back to, and what electrical designs are made from. Your changes already save as you go; this keeps them as they are now.',
  action = 'Save version',
}: {
  onSave: (note: string) => Promise<void>
  onClose: () => void
  title?: string
  explanation?: string
  action?: string
}) {
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await onSave(note.trim())
    } catch (err) {
      setError((err as Error).message)
      setBusy(false)
    }
  }

  return (
    <Dialog title={title} onClose={onClose}>
      <form onSubmit={submit}>
        <p className="mt-1 text-xs text-slate-500">{explanation}</p>
        <label className="mt-4 block">
          <span className="mb-1 block text-[11px] font-medium uppercase tracking-wide text-slate-500">Note (optional)</span>
          <input className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm" value={note} maxLength={500}
            onChange={(e) => setNote(e.target.value)} placeholder="e.g. Checked against the builder's drawings" autoFocus />
        </label>
        {error && <p className="mt-2 text-xs text-red-600" role="alert">{error}</p>}
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100">Cancel</button>
          <button type="submit" disabled={busy} className="rounded bg-blue-600 px-3.5 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60">
            {busy ? 'Saving…' : action}
          </button>
        </div>
      </form>
    </Dialog>
  )
}

function StageChip({ stage }: { stage: HouseStage }) {
  const tone = stage === 'floor_plan_approved' ? 'ok' : stage === 'awaiting_upload' ? 'muted' : 'warn'
  return <Chip tone={tone} title="Where this house is">{STAGE_LABELS[stage] ?? stage}</Chip>
}

function HistoryDialog({ houseId, canRestore, onRestore, onClose }: {
  houseId: string; canRestore: boolean; onRestore: (versionNo: number) => Promise<void>; onClose: () => void
}) {
  const [tab, setTab] = useState<'versions' | 'stages'>('versions')
  return (
    <Dialog title="Floor-plan history" onClose={onClose}>
      <div role="tablist" aria-label="History" className="mt-3 flex gap-1 border-b border-slate-200">
        {(['versions', 'stages'] as const).map((t) => (
          <button key={t} type="button" role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
            className={`-mb-px border-b-2 px-3 py-1.5 text-sm font-medium ${tab === t ? 'border-blue-600 text-blue-700' : 'border-transparent text-slate-500 hover:text-slate-700'}`}>
            {t === 'versions' ? 'Versions' : 'Stages'}
          </button>
        ))}
      </div>
      {tab === 'versions'
        ? <VersionsTab houseId={houseId} canRestore={canRestore} onRestore={onRestore} />
        : <StagesTab houseId={houseId} />}
      <div className="mt-4 flex justify-end">
        <button type="button" onClick={onClose} className="rounded px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100">Close</button>
      </div>
    </Dialog>
  )
}

function StagesTab({ houseId }: { houseId: string }) {
  const [events, setEvents] = useState<StageEvent[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    houseStages(houseId).then(setEvents).catch((e: Error) => setError(e.message))
  }, [houseId])
  if (error) return <p className="mt-3 text-xs text-red-600" role="alert">{error}</p>
  if (!events) return <p className="mt-3 text-sm text-slate-500">Loading…</p>
  return (
    <ol className="mt-3 max-h-96 divide-y divide-slate-100 overflow-auto">
      {events.map((e, i) => (
        <li key={i} className="py-2.5">
          <p className="text-sm font-medium text-slate-900">{STAGE_LABELS[e.to] ?? e.to}</p>
          <p className="text-xs text-slate-500">
            {e.from ? `from ${STAGE_LABELS[e.from] ?? e.from} · ` : 'first stage · '}
            {e.byName ?? 'the system'}{' · '}
            {new Date(e.at).toLocaleString('en-AU', { dateStyle: 'medium', timeStyle: 'short' })}
            {e.note ? ` · ${e.note}` : ''}
          </p>
        </li>
      ))}
    </ol>
  )
}

function VersionsTab({ houseId, canRestore, onRestore }: {
  houseId: string; canRestore: boolean; onRestore: (versionNo: number) => Promise<void>
}) {
  const [versions, setVersions] = useState<FloorPlanVersion[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<number | null>(null)

  useEffect(() => {
    floorPlanHistory(houseId).then(setVersions).catch((e: Error) => setError(e.message))
  }, [houseId])

  const restore = async (versionNo: number) => {
    setBusy(versionNo)
    setError(null)
    try {
      await onRestore(versionNo)
    } catch (e) {
      setError((e as Error).message)
      setBusy(null)
    }
  }

  return (
    <>
      {error && <p className="mt-2 text-xs text-red-600" role="alert">{error}</p>}
      {!versions && !error && <p className="mt-3 text-sm text-slate-500">Loading…</p>}
      {versions && versions.length === 0 && <p className="mt-3 text-sm text-slate-500">Nothing saved yet.</p>}
      {versions && versions.length > 0 && (
        <ul className="mt-3 max-h-96 divide-y divide-slate-100 overflow-auto">
          {versions.map((v) => (
            <li key={v.versionNo} className="flex items-center gap-3 py-2.5">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-slate-900">
                  {v.state === 'draft' ? 'Draft' : `Version ${v.versionNo}`}
                  {v.current && <span className="ml-2 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-medium text-emerald-700">Current</span>}
                </p>
                <p className="truncate text-xs text-slate-500">
                  {v.note ? `${v.note} · ` : ''}{v.rooms} rooms{v.floorAreaM2 !== null ? ` · ${v.floorAreaM2} m²` : ''}
                  {' · '}{new Date(v.savedAt).toLocaleString('en-AU', { dateStyle: 'medium', timeStyle: 'short' })}
                </p>
              </div>
              {canRestore && v.state === 'committed' && (
                <button type="button" onClick={() => void restore(v.versionNo)} disabled={busy !== null}
                  className="rounded border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50">
                  {busy === v.versionNo ? 'Restoring…' : 'Restore'}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {canRestore && versions && versions.some((v) => v.state === 'committed') && (
        <p className="mt-3 text-xs text-slate-500">Restoring puts that version in the editor as the draft; the versions themselves never change.</p>
      )}
    </>
  )
}
