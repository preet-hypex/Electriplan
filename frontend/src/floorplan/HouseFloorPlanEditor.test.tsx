import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useEditor } from './state/store'
import type { FloorPlan } from './model/types'
import type { HouseMode } from './FloorPlanApp'

const mocks = vi.hoisted(() => ({
  openFloorPlan: vi.fn(), saveFloorPlanDraft: vi.fn(), saveFloorPlanVersion: vi.fn(),
  floorPlanHistory: vi.fn(), restoreFloorPlanVersion: vi.fn(), uploadFloorPlanImage: vi.fn(),
}))
vi.mock('../api/floorPlans', () => mocks)

// The canvas is not what these tests are about: a stand-in shows what the page gives the editor.
vi.mock('./FloorPlanApp', () => ({
  default: ({ house }: { house: HouseMode }) => (
    <div>
      <h2>{house.title}</h2>
      <div data-testid="status">{house.status}</div>
      {house.banner}
      <div data-testid="actions">{house.actions}</div>
      <button type="button" onClick={() => void house.analyse(new File(['png'], 'plan.png', { type: 'image/png' }))
        .then(plan => useEditor.getState().loadPlan(plan))}>Upload stand-in</button>
    </div>
  ),
}))

import HouseFloorPlanEditor from './HouseFloorPlanEditor'

const PLAN: FloorPlan = {
  version: 1, units: 'mm', walls: [], doors: [], windows: [], openings: [], labels: [], dimensions: [],
  rooms: [{ id: 'r1', name: 'Kitchen', polygon: [{ x: 0, y: 0 }, { x: 3000, y: 0 }, { x: 3000, y: 3000 }], labelPosition: { x: 1000, y: 1000 } }],
}

const doc = (state: 'draft' | 'committed', version: number, versionNo = 2, plan: FloorPlan = PLAN) => ({
  houseId: 'h1', levelId: 'l1', versionNo, state, version, basedOnVersionNo: null,
  document: plan as unknown as Record<string, unknown>, savedAt: '2026-10-08T00:00:00Z', savedBy: null,
})

const renderEditor = (canEdit = true) =>
  render(<HouseFloorPlanEditor houseId="h1" title="Type A · floor plan" canEdit={canEdit} getAccessToken={async () => 't'} />)

describe('HouseFloorPlanEditor', () => {
  beforeEach(() => {
    Object.values(mocks).forEach(m => m.mockReset())
    mocks.openFloorPlan.mockResolvedValue(doc('draft', 4))
    mocks.saveFloorPlanDraft.mockImplementation(async () => doc('draft', 5))
    mocks.saveFloorPlanVersion.mockResolvedValue({})
    mocks.floorPlanHistory.mockResolvedValue([
      { versionNo: 2, state: 'draft', current: false, note: null, rooms: 1, walls: 0, openings: 0, floorAreaM2: 9, openChecks: 0, savedAt: '2026-10-08T00:00:00Z', savedBy: null, committedAt: null },
      { versionNo: 1, state: 'committed', current: true, note: 'First', rooms: 3, walls: 4, openings: 1, floorAreaM2: 40, openChecks: 0, savedAt: '2026-10-07T00:00:00Z', savedBy: null, committedAt: '2026-10-07T00:00:00Z' },
    ])
  })

  it('opens the house’s plan in the editor, as already saved', async () => {
    renderEditor()
    expect(await screen.findByRole('heading', { name: 'Type A · floor plan' })).toBeInTheDocument()
    expect(mocks.openFloorPlan).toHaveBeenCalledWith('h1')
    expect(useEditor.getState().plan.rooms[0].name).toBe('Kitchen')
    expect(screen.getByTestId('status')).toHaveTextContent('All changes saved')
    expect(mocks.saveFloorPlanDraft).not.toHaveBeenCalled()
  })

  it('starts from an empty plan when the house has none yet', async () => {
    mocks.openFloorPlan.mockRejectedValue(Object.assign(new Error('This house has no floor plan yet.'), { status: 404 }))
    renderEditor()
    await screen.findByRole('heading', { name: 'Type A · floor plan' })
    expect(useEditor.getState().plan.rooms).toHaveLength(0)
    expect(screen.getByRole('button', { name: /save version/i })).toBeDisabled()
  })

  it('saves an edit as the draft, with the draft’s version', async () => {
    renderEditor()
    await screen.findByText('✓ All changes saved')
    act(() => useEditor.getState().commit(p => { p.rooms[0].name = 'Kitchen & dining' }))
    await waitFor(() => expect(mocks.saveFloorPlanDraft).toHaveBeenCalled(), { timeout: 3000 })
    const [houseId, plan, version] = mocks.saveFloorPlanDraft.mock.calls[0]
    expect(houseId).toBe('h1')
    expect(plan.rooms[0].name).toBe('Kitchen & dining')
    expect(version).toBe(4)
  })

  it('saves a version with a note, from the draft as last saved', async () => {
    renderEditor()
    await userEvent.click(await screen.findByRole('button', { name: /save version/i }))
    const dialog = screen.getByRole('dialog', { name: 'Save a version' })
    await userEvent.type(within(dialog).getByRole('textbox'), 'Checked')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save version' }))

    await waitFor(() => expect(mocks.saveFloorPlanVersion).toHaveBeenCalledWith('h1', 4, 'Checked'))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByRole('button', { name: /save version/i })).toBeDisabled()
  })

  it('restores an earlier version as the draft', async () => {
    const studio = { ...PLAN, rooms: [{ ...PLAN.rooms[0], id: 'r9', name: 'Studio' }] }
    mocks.restoreFloorPlanVersion.mockResolvedValue(doc('draft', 6, 2, studio))
    renderEditor()
    await userEvent.click(await screen.findByRole('button', { name: /history/i }))
    const dialog = screen.getByRole('dialog', { name: 'Floor-plan history' })
    expect(await within(dialog).findByText('Version 1')).toBeInTheDocument()
    expect(within(dialog).getByText('Current')).toBeInTheDocument()
    expect(within(dialog).getAllByRole('button', { name: 'Restore' })).toHaveLength(1)

    await userEvent.click(within(dialog).getByRole('button', { name: 'Restore' }))
    await waitFor(() => expect(mocks.restoreFloorPlanVersion).toHaveBeenCalledWith('h1', 1, 4))
    expect(useEditor.getState().plan.rooms[0].name).toBe('Studio')
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('uploads an image through the API, whose analysed plan is already the saved draft', async () => {
    const analysed = { ...PLAN, rooms: [{ ...PLAN.rooms[0], id: 'r7', name: 'Analysed' }] }
    mocks.uploadFloorPlanImage.mockResolvedValue(doc('draft', 7, 2, analysed))
    renderEditor()
    await screen.findByText('✓ All changes saved')
    await userEvent.click(screen.getByRole('button', { name: 'Upload stand-in' }))

    await waitFor(() => expect(useEditor.getState().plan.rooms[0].name).toBe('Analysed'))
    const [houseId, file, version] = mocks.uploadFloorPlanImage.mock.calls[0]
    expect(houseId).toBe('h1')
    expect(file.name).toBe('plan.png')
    expect(version).toBe(4)
    await new Promise(r => setTimeout(r, 1700))
    expect(mocks.saveFloorPlanDraft).not.toHaveBeenCalled()
    expect(screen.getByTestId('status')).toHaveTextContent('All changes saved')
  })

  it('lets people who cannot edit look, without saving', async () => {
    renderEditor(false)
    await screen.findByText('View only')
    expect(screen.getByText(/changes are not saved/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /save version/i })).toBeNull()
    act(() => useEditor.getState().commit(p => { p.rooms[0].name = 'x' }))
    await new Promise(r => setTimeout(r, 1700))
    expect(mocks.saveFloorPlanDraft).not.toHaveBeenCalled()
  })

  it('offers to reload when someone else saved meanwhile', async () => {
    mocks.saveFloorPlanDraft.mockRejectedValue(Object.assign(new Error('Someone changed this'), { status: 409 }))
    renderEditor()
    await screen.findByText('✓ All changes saved')
    act(() => useEditor.getState().commit(p => { p.rooms[0].name = 'Mine' }))
    expect(await screen.findByRole('alert', {}, { timeout: 3000 })).toHaveTextContent(/someone else saved this plan/i)

    mocks.openFloorPlan.mockResolvedValue(doc('draft', 9))
    await userEvent.click(screen.getByRole('button', { name: 'Reload' }))
    await waitFor(() => expect(mocks.openFloorPlan).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('✓ All changes saved')).toBeInTheDocument()
    expect(useEditor.getState().plan.rooms[0].name).toBe('Kitchen')
  })
})
