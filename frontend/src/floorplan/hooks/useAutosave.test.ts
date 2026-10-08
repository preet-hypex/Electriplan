import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useEditor } from '../state/store'
import { useAutosave } from './useAutosave'
import type { FloorPlan } from '../model/types'

const EMPTY: FloorPlan = {
  version: 1, units: 'mm', walls: [], rooms: [], doors: [], windows: [], openings: [], labels: [], dimensions: [],
}

/** An edit, as the editor makes one: a new plan in the store, marked dirty. */
function edit(name: string) {
  act(() => useEditor.getState().commit(plan => {
    plan.labels.push({ id: `l${plan.labels.length}`, text: name, position: { x: 0, y: 0 }, type: 'other' } as FloorPlan['labels'][number])
  }))
}

async function wait(ms: number) {
  await act(async () => { await vi.advanceTimersByTimeAsync(ms) })
}

describe('useAutosave', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    useEditor.getState().loadPlan(structuredClone(EMPTY), 'Test')
  })
  afterEach(() => vi.useRealTimers())

  it('saves a moment after editing pauses, once, and then says saved', async () => {
    const save = vi.fn().mockResolvedValue(undefined)
    const { result } = renderHook(() => useAutosave({ save, enabled: true, delay: 1000 }))
    expect(result.current.state).toBe('saved')

    edit('a')
    expect(result.current.state).toBe('pending')
    await wait(500)
    edit('b')
    await wait(500)
    expect(save).not.toHaveBeenCalled()
    await wait(600)

    expect(save).toHaveBeenCalledTimes(1)
    expect(save.mock.calls[0][0].labels.map((l: { text: string }) => l.text)).toEqual(['a', 'b'])
    expect(result.current.state).toBe('saved')
    expect(useEditor.getState().dirty).toBe(false)
  })

  it('saves edits made during a save with the next save', async () => {
    let finish: () => void = () => {}
    const save = vi.fn()
      .mockImplementationOnce(() => new Promise<void>(resolve => { finish = resolve }))
      .mockResolvedValue(undefined)
    const { result } = renderHook(() => useAutosave({ save, enabled: true, delay: 100 }))

    edit('a')
    await wait(150)
    expect(result.current.state).toBe('saving')
    edit('b')
    await act(async () => { finish() })
    expect(result.current.state).toBe('pending')
    await wait(150)
    expect(save).toHaveBeenCalledTimes(2)
    expect(save.mock.calls[1][0].labels).toHaveLength(2)
    expect(result.current.state).toBe('saved')
  })

  it('waits until a drag ends', async () => {
    const save = vi.fn().mockResolvedValue(undefined)
    renderHook(() => useAutosave({ save, enabled: true, delay: 100 }))
    act(() => useEditor.getState().beginInteraction())
    edit('mid-drag')
    await wait(500)
    expect(save).not.toHaveBeenCalled()
    act(() => useEditor.getState().endInteraction())
    await wait(150)
    expect(save).toHaveBeenCalledTimes(1)
  })

  it('treats a newly loaded plan as unsaved, unless it is marked as the saved one', async () => {
    const save = vi.fn().mockResolvedValue(undefined)
    const { result } = renderHook(() => useAutosave({ save, enabled: true, delay: 100 }))

    act(() => useEditor.getState().loadPlan({ ...structuredClone(EMPTY), labels: [] }, 'Analysed'))
    await wait(150)
    expect(save).toHaveBeenCalledTimes(1)

    act(() => {
      useEditor.getState().loadPlan(structuredClone(EMPTY), 'From the server')
      result.current.setSaved(useEditor.getState().plan)
    })
    await wait(150)
    expect(save).toHaveBeenCalledTimes(1)
  })

  it('reports a failure, and tries again on the next edit', async () => {
    const save = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue(undefined)
    const { result } = renderHook(() => useAutosave({ save, enabled: true, delay: 100 }))
    edit('a')
    await wait(150)
    expect(result.current.state).toBe('failed')
    expect(result.current.error).toBe('offline')

    edit('b')
    await wait(150)
    expect(save).toHaveBeenCalledTimes(2)
    expect(result.current.state).toBe('saved')
    expect(result.current.error).toBeNull()
  })

  it('stops saving when someone else saved meanwhile', async () => {
    const save = vi.fn().mockRejectedValue(Object.assign(new Error('Someone changed this'), { status: 409 }))
    const { result } = renderHook(() => useAutosave({ save, enabled: true, delay: 100 }))
    edit('a')
    await wait(150)
    expect(result.current.state).toBe('conflict')
    edit('b')
    await wait(500)
    expect(save).toHaveBeenCalledTimes(1)
  })

  it('saves nothing when turned off', async () => {
    const save = vi.fn().mockResolvedValue(undefined)
    renderHook(() => useAutosave({ save, enabled: false, delay: 100 }))
    edit('a')
    await wait(500)
    expect(save).not.toHaveBeenCalled()
  })

  it('saves at once on flush, and saves what is left when the page closes', async () => {
    const save = vi.fn().mockResolvedValue(undefined)
    const { result, unmount } = renderHook(() => useAutosave({ save, enabled: true, delay: 10_000 }))
    edit('a')
    await act(async () => { await result.current.flush() })
    expect(save).toHaveBeenCalledTimes(1)

    edit('b')
    unmount()
    await act(async () => { await vi.advanceTimersByTimeAsync(0) })
    expect(save).toHaveBeenCalledTimes(2)
  })
})
