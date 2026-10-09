import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useEditor } from './state/store'
import type { FloorPlan } from './model/types'
import type { HouseMode } from './FloorPlanApp'

const mocks = vi.hoisted(() => ({
  openFloorPlan: vi.fn(), saveFloorPlanDraft: vi.fn(), saveFloorPlanVersion: vi.fn(),
  floorPlanHistory: vi.fn(), restoreFloorPlanVersion: vi.fn(), uploadFloorPlanImage: vi.fn(), approveFloorPlan: vi.fn(),
  getFloorPlanVersion: vi.fn(),
  houseStages: vi.fn(),
}))
vi.mock('../api/floorPlans', () => mocks)
vi.mock('../api/projects', async (original) => ({ ...(await original<object>()), houseStages: mocks.houseStages }))

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

const doc = (state: 'draft' | 'committed', version: number, versionNo = 2, plan: FloorPlan = PLAN,
  houseStage: 'floor_plan_review' | 'floor_plan_approved' = 'floor_plan_review', unsavedChanges = state === 'draft') => ({
  houseId: 'h1', levelId: 'l1', versionNo, state, version, basedOnVersionNo: null,
  document: plan as unknown as Record<string, unknown>, savedAt: '2026-10-08T00:00:00Z', savedBy: null, houseStage, unsavedChanges,
})

const renderEditor = (canEdit = true) =>
  render(<HouseFloorPlanEditor houseId="h1" title="Type A · floor plan" stage="floor_plan_review" canEdit={canEdit} getAccessToken={async () => 't'} />)

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

  const STUDIO = { ...PLAN, rooms: [{ ...PLAN.rooms[0], id: 'r9', name: 'Studio' }] }

  async function openHistoryAndRestore() {
    await userEvent.click(await screen.findByRole('button', { name: /history/i }))
    const dialog = screen.getByRole('dialog', { name: 'Floor-plan history' })
    expect(await within(dialog).findByText('Version 1')).toBeInTheDocument()
    expect(within(dialog).getByText('Current')).toBeInTheDocument()
    expect(within(dialog).getAllByRole('button', { name: 'Restore' })).toHaveLength(1)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Restore' }))
    return dialog
  }

  it('asks before restoring over a draft with unsaved changes, and keeps them when asked to', async () => {
    mocks.restoreFloorPlanVersion.mockResolvedValue({ draft: doc('draft', 0, 3, STUDIO, 'floor_plan_review', false), keptAsVersionNo: 2 })
    renderEditor()
    const dialog = await openHistoryAndRestore()

    expect(await within(dialog).findByText('Your draft has changes not saved as a version')).toBeInTheDocument()
    expect(mocks.restoreFloorPlanVersion).not.toHaveBeenCalled()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save as a version, then restore' }))

    await waitFor(() => expect(mocks.restoreFloorPlanVersion).toHaveBeenCalledWith('h1', 1, 4, true))
    expect(useEditor.getState().plan.rooms[0].name).toBe('Studio')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByRole('status')).toHaveTextContent('Your earlier draft was saved as version 2')
    expect(screen.getByRole('button', { name: /save version/i })).toBeDisabled()
  })

  it('restores without saving when the person lets the changes go', async () => {
    mocks.restoreFloorPlanVersion.mockResolvedValue({ draft: doc('draft', 5, 2, STUDIO, 'floor_plan_review', false), keptAsVersionNo: null })
    renderEditor()
    const dialog = await openHistoryAndRestore()
    await userEvent.click(await within(dialog).findByRole('button', { name: 'Restore without saving' }))
    await waitFor(() => expect(mocks.restoreFloorPlanVersion).toHaveBeenCalledWith('h1', 1, 4, false))
    expect(screen.getByRole('status')).toHaveTextContent('Version 1 is back in the editor.')
  })

  it('cancels the restore from the question', async () => {
    renderEditor()
    const dialog = await openHistoryAndRestore()
    await userEvent.click(await within(dialog).findByRole('button', { name: 'Cancel' }))
    expect(within(dialog).getByText('Version 1')).toBeInTheDocument()
    expect(mocks.restoreFloorPlanVersion).not.toHaveBeenCalled()
  })

  it('restores straight away when the draft has no unsaved changes', async () => {
    mocks.openFloorPlan.mockResolvedValue(doc('draft', 4, 2, PLAN, 'floor_plan_review', false))
    mocks.restoreFloorPlanVersion.mockResolvedValue({ draft: doc('draft', 5, 2, STUDIO, 'floor_plan_review', false), keptAsVersionNo: null })
    renderEditor()
    expect(await screen.findByRole('button', { name: /save version/i })).toBeDisabled()
    await openHistoryAndRestore()
    await waitFor(() => expect(mocks.restoreFloorPlanVersion).toHaveBeenCalledWith('h1', 1, 4, undefined))
    expect(screen.queryByText('Your draft has changes not saved as a version')).toBeNull()
  })

  it('shows a version read-only before restoring it', async () => {
    mocks.getFloorPlanVersion.mockResolvedValue(doc('committed', 0, 1, STUDIO))
    mocks.openFloorPlan.mockResolvedValue(doc('draft', 4, 2, PLAN, 'floor_plan_review', false))
    mocks.restoreFloorPlanVersion.mockResolvedValue({ draft: doc('draft', 5, 2, STUDIO, 'floor_plan_review', false), keptAsVersionNo: null })
    renderEditor()
    await userEvent.click(await screen.findByRole('button', { name: /history/i }))
    const dialog = screen.getByRole('dialog', { name: 'Floor-plan history' })
    await userEvent.click(await within(dialog).findByRole('button', { name: 'View' }))

    const drawing = await within(dialog).findByRole('img', { name: 'Version 1 of the floor plan' })
    expect(within(drawing).getByText('Studio')).toBeInTheDocument()
    expect(mocks.getFloorPlanVersion).toHaveBeenCalledWith('h1', 1)
    expect(useEditor.getState().plan.rooms[0].name).toBe('Kitchen')

    await userEvent.click(within(dialog).getByRole('button', { name: 'Back to the list' }))
    await userEvent.click(within(dialog).getByRole('button', { name: 'View' }))
    await userEvent.click(await within(dialog).findByRole('button', { name: 'Restore this version' }))
    await waitFor(() => expect(mocks.restoreFloorPlanVersion).toHaveBeenCalledWith('h1', 1, 4, undefined))
    expect(useEditor.getState().plan.rooms[0].name).toBe('Studio')
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

  it('shows where the house is, and approves a plan being checked', async () => {
    mocks.approveFloorPlan.mockResolvedValue(doc('committed', 0, 2, PLAN, 'floor_plan_approved'))
    renderEditor()
    expect(await screen.findByText('Checking floor plan')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /approve floor plan/i }))
    const dialog = screen.getByRole('dialog', { name: 'Approve the floor plan' })
    await userEvent.type(within(dialog).getByRole('textbox'), 'Matches the drawings')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Approve' }))

    await waitFor(() => expect(mocks.approveFloorPlan).toHaveBeenCalledWith('h1', 4, 'Matches the drawings'))
    expect(await screen.findByText('Floor plan approved')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /approve floor plan/i })).toBeNull()
    expect(screen.getByRole('button', { name: /save version/i })).toBeDisabled()
  })

  it('moves the stage when a save does (an approved plan edited goes back for checking)', async () => {
    mocks.openFloorPlan.mockResolvedValue(doc('committed', 0, 1, PLAN, 'floor_plan_approved'))
    renderEditor()
    expect(await screen.findByText('Floor plan approved')).toBeInTheDocument()
    act(() => useEditor.getState().commit(p => { p.rooms[0].name = 'Kitchen 2' }))
    expect(await screen.findByText('Checking floor plan', {}, { timeout: 3000 })).toBeInTheDocument()
  })

  it('lists the house’s stage history', async () => {
    mocks.houseStages.mockResolvedValue([
      { from: 'awaiting_upload', to: 'floor_plan_review', at: '2026-10-08T01:00:00Z', by: 'u1', byName: 'Bea Builder', note: null },
      { from: null, to: 'awaiting_upload', at: '2026-10-08T00:00:00Z', by: 'u1', byName: 'Bea Builder', note: null },
    ])
    renderEditor()
    await userEvent.click(await screen.findByRole('button', { name: /history/i }))
    await userEvent.click(screen.getByRole('tab', { name: 'Stages' }))
    const dialog = screen.getByRole('dialog', { name: 'Floor-plan history' })
    expect(await within(dialog).findByText(/from Awaiting floor plan/)).toHaveTextContent('Bea Builder')
    expect(within(dialog).getByText(/first stage/)).toBeInTheDocument()
    expect(mocks.houseStages).toHaveBeenCalledWith('h1')
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
