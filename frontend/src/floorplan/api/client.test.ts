import { afterEach, describe, expect, it, vi } from 'vitest'
import { calibrate, fetchImageObjectUrl, health, setAccessTokenProvider } from './client'

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

  it('checks health without a token', async () => {
    const fetchMock = mockFetch({ status: 'ok' })
    setAccessTokenProvider(async () => 'token-123')
    await health()
    expect(headersOf(fetchMock.mock.calls[0]).get('Authorization')).toBeNull()
  })
})
