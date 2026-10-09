import { useCallback, useEffect, useRef, useState } from 'react'
import { useEditor } from '../state/store'
import type { FloorPlan } from '../model/types'

export type SaveState = 'saved' | 'pending' | 'saving' | 'failed' | 'conflict'

interface Options {
  /** Saves the plan; rejects with `status: 409` when someone else saved since. */
  save: (plan: FloorPlan) => Promise<void>
  /** Off (e.g. a viewer, or an archived house): nothing is saved. */
  enabled: boolean
  /** How long editing must pause before saving. */
  delay?: number
}

/**
 * Saves the editor's plan a moment after editing pauses, so nothing is lost
 * and nothing needs a Save button. "Changed" means different from the plan
 * last saved (or loaded), not the editor's dirty flag, so a freshly analysed
 * or imported plan is saved too. Nothing is saved mid-drag, and saves never
 * overlap: edits made during one are saved by the next.
 *
 * Call `setSaved(plan)` after loading or restoring a plan from the server: it
 * becomes the plan that is already saved.
 */
export function useAutosave({ save, enabled, delay = 1500 }: Options) {
  const plan = useEditor((s) => s.plan)
  const dragging = useEditor((s) => s.interactionBaseline !== null)
  const [state, setState] = useState<SaveState>('saved')
  const [error, setError] = useState<string | null>(null)

  const saved = useRef<FloorPlan>(useEditor.getState().plan)
  const running = useRef<Promise<void> | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const saveRef = useRef(save)
  saveRef.current = save

  const run = useCallback(async (): Promise<void> => {
    if (running.current) {
      await running.current
    }
    const snapshot = useEditor.getState().plan
    if (snapshot === saved.current) {
      setState('saved')
      return
    }
    setState('saving')
    const attempt = (async () => {
      try {
        await saveRef.current(snapshot)
        saved.current = snapshot
        setError(null)
        if (useEditor.getState().plan === snapshot) {
          useEditor.getState().markSaved()
          setState('saved')
        } else {
          setState('pending') // edited while saving: the effect schedules the next save
        }
      } catch (e) {
        const status = (e as { status?: number }).status
        setError((e as Error).message)
        setState(status === 409 ? 'conflict' : 'failed')
      }
    })()
    running.current = attempt
    try {
      await attempt
    } finally {
      running.current = null
    }
  }, [])

  // Schedule a save when the plan differs from the saved one and editing has paused.
  useEffect(() => {
    if (!enabled || state === 'conflict') return undefined
    if (plan === saved.current) return undefined
    if (state !== 'saving') setState('pending')
    if (dragging) return undefined
    timer.current = setTimeout(() => void run(), delay)
    return () => {
      if (timer.current) clearTimeout(timer.current)
    }
    // state is read, not reacted to: only plan, dragging and enabled schedule saves.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [plan, dragging, enabled, delay, run])

  // Leaving the page with unsaved edits: save them without waiting. Only on
  // leaving (not when saving is switched off, e.g. while reloading), and not
  // after someone else saved: those edits would be refused anyway.
  const enabledNow = useRef(enabled)
  enabledNow.current = enabled
  const stateNow = useRef(state)
  stateNow.current = state
  useEffect(() => () => {
    if (enabledNow.current && stateNow.current !== 'conflict' && useEditor.getState().plan !== saved.current) void run()
  }, [run])

  /** Save now (before "Save version" or a restore); resolves once the plan is saved, or rejects. */
  const flush = useCallback(async () => {
    if (timer.current) clearTimeout(timer.current)
    await run()
    if (useEditor.getState().plan !== saved.current) {
      throw new Error(error ?? 'The plan could not be saved.')
    }
  }, [run, error])

  const setSaved = useCallback((p: FloorPlan) => {
    saved.current = p
    setError(null)
    setState('saved')
  }, [])

  return { state, error, flush, setSaved }
}
