import { afterEach, describe, expect, it, vi } from 'vitest'
import { LOCAL_IMAGE, analyse, calibrate, fetchImageObjectUrl, health, setAccessTokenProvider } from './client'
import { setCompany } from '../../lib/api'
import { sampleFloorPlan } from '../model/sample'

function mockFetch(body: unknown = { mm_per_px: 10, factor: 1, confidence: 1 }) {
  const fetchMock = vi.fn(async () => new Response(JSON.stringify(body), { status: 200 }))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const headersOf = (call: unknown[]) => new Headers((call[1] as RequestInit | undefined)?.headers)

describe('analyser client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    setAccessTokenProvider(async () => null)
  })

  it('sends the signed-in user’s access token', async () => {
    const fetchMock = mockFetch()
    setAccessTokenProvider(async () => 'token-123')
    await calibrate({ pixels: 100, millimetres: 1000 })
    expect(headersOf(fetchMock.mock.calls[0]).get('Authorization')).toBe('Bearer token-123')
  })

  it('asks for the token on every call, so a refreshed one is used', async () => {
    const fetchMock = mockFetch()
    let n = 0
    setAccessTokenProvider(async () => `token-${++n}`)
    await calibrate({ pixels: 100, millimetres: 1000 })
    await calibrate({ pixels: 100, millimetres: 1000 })
    expect(headersOf(fetchMock.mock.calls[1]).get('Authorization')).toBe('Bearer token-2')
  })

  it('fetches plan images with the token too', async () => {
    const fetchMock = vi.fn(async () => new Response('png', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    // jsdom has no createObjectURL; give it one for this test.
    Object.defineProperty(URL, 'createObjectURL', { value: () => 'blob:plan', configurable: true })
    setAccessTokenProvider(async () => 'token-123')
    expect(await fetchImageObjectUrl('/api/floorplan/images/a.png')).toBe('blob:plan')
    expect(headersOf(fetchMock.mock.calls[0]).get('Authorization')).toBe('Bearer token-123')
  })

  it('fetches a house’s image (a company file) in the chosen company', async () => {
    const fetchMock = vi.fn(async () => new Response('png', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    Object.defineProperty(URL, 'createObjectURL', { value: () => 'blob:plan', configurable: true })
    setCompany('c0ffee00-0000-4000-8000-000000000001')
    try {
      await fetchImageObjectUrl('/api/files/f1')
      expect(headersOf(fetchMock.mock.calls[0]).get('X-Organisation-Id')).toBe('c0ffee00-0000-4000-8000-000000000001')
    } finally {
      setCompany(null)
    }
  })

  it('shows the scratch editor’s image from the picked file, since the analyser keeps none', async () => {
    const plan = sampleFloorPlan()
    const analysed = { ...plan, source: { ...(plan.source ?? {
      imageWidth: 100, imageHeight: 100, planRegion: { x: 0, y: 0, width: 100, height: 100 },
      mmPerPx: 10, scaleConfidence: 0.9, scaleMethod: 'manual',
    }), imageUrl: LOCAL_IMAGE } }
    mockFetch(analysed)
    Object.defineProperty(URL, 'createObjectURL', { value: () => 'blob:picked', configurable: true })
    const result = await analyse(new File(['png'], 'plan.png', { type: 'image/png' }))
    expect(result.source?.imageUrl).toBe('blob:picked')
  })

  it('checks health without a token', async () => {
    const fetchMock = mockFetch({ status: 'ok' })
    setAccessTokenProvider(async () => 'token-123')
    await health()
    expect(headersOf(fetchMock.mock.calls[0]).get('Authorization')).toBeNull()
  })
})
