import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ findAddresses: vi.fn() }))
vi.mock('../../api/projects', () => ({ findAddresses: mocks.findAddresses }))

import AddressSearch from './AddressSearch'

const GLENLYON = {
  label: '12 Glenlyon Road, Brunswick VIC 3056', street: '12 Glenlyon Road', suburb: 'Brunswick',
  state: 'VIC', postcode: '3056', latitude: -37.77, longitude: 144.96,
}
const SPRING = { ...GLENLYON, label: '1 Spring Street, Melbourne VIC 3000', street: '1 Spring Street', suburb: 'Melbourne', postcode: '3000' }

describe('AddressSearch', () => {
  // A block body: an arrow returning the mock would make Vitest call it as a cleanup step.
  beforeEach(() => {
    mocks.findAddresses.mockReset().mockResolvedValue([GLENLYON, SPRING])
  })

  it('suggests addresses once three characters are typed, and fills the site with the one picked', async () => {
    const onPick = vi.fn()
    render(<AddressSearch onPick={onPick} />)
    const box = screen.getByRole('combobox', { name: 'Find the address' })

    await userEvent.type(box, '12')
    await new Promise(r => setTimeout(r, 350))
    expect(mocks.findAddresses).not.toHaveBeenCalled()

    await userEvent.type(box, ' Glen')
    await userEvent.click(await screen.findByRole('option', { name: GLENLYON.label }))

    expect(mocks.findAddresses).toHaveBeenLastCalledWith('12 Glen')
    expect(onPick).toHaveBeenCalledWith(GLENLYON)
    expect(box).toHaveValue(GLENLYON.label)
    expect(screen.queryByRole('listbox')).toBeNull()
    expect(screen.getByText('Check the house number.')).toBeInTheDocument()
  })

  it('is used from the keyboard', async () => {
    const onPick = vi.fn()
    render(<AddressSearch onPick={onPick} />)
    const box = screen.getByRole('combobox', { name: 'Find the address' })
    await userEvent.type(box, 'spring')
    await screen.findByRole('listbox', { name: 'Matching addresses' })

    await userEvent.keyboard('{ArrowDown}{ArrowDown}')
    expect(screen.getByRole('option', { name: SPRING.label })).toHaveAttribute('aria-selected', 'true')
    expect(box).toHaveAttribute('aria-activedescendant')
    await userEvent.keyboard('{Enter}')
    expect(onPick).toHaveBeenCalledWith(SPRING)
  })

  it('says when nothing matches, and when the finder is unavailable', async () => {
    mocks.findAddresses.mockResolvedValue([])
    render(<AddressSearch onPick={vi.fn()} />)
    const box = screen.getByRole('combobox', { name: 'Find the address' })
    await userEvent.type(box, 'nowhere at all')
    expect(await screen.findByText(/no matching address found/i)).toBeInTheDocument()

    mocks.findAddresses.mockRejectedValue(new Error('Address search is not available right now. Type the address instead.'))
    await userEvent.type(box, 's')
    expect(await screen.findByText('Address search is not available right now. Type the address instead.')).toBeInTheDocument()
  })

  it('shows only the newest answer when an older one arrives late', async () => {
    let answerFirst
    mocks.findAddresses
      .mockImplementationOnce(() => new Promise(resolve => { answerFirst = resolve }))
      .mockResolvedValueOnce([SPRING])
    render(<AddressSearch onPick={vi.fn()} />)
    const box = screen.getByRole('combobox', { name: 'Find the address' })
    await userEvent.type(box, 'glen')
    await waitFor(() => expect(mocks.findAddresses).toHaveBeenCalledTimes(1))
    await userEvent.type(box, 'x spring')
    await screen.findByRole('option', { name: SPRING.label })
    answerFirst([GLENLYON])
    await new Promise(r => setTimeout(r, 20))
    expect(screen.queryByRole('option', { name: GLENLYON.label })).toBeNull()
  })
})
