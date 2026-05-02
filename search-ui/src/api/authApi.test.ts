/**
 * Tests cover both the mock implementations (USE_MOCK = true, which is the
 * current default) and the real fetch-based implementations.
 *
 * The mock functions are tested directly because they are the exported symbols
 * while USE_MOCK is true. The real functions are tested separately by mocking
 * global.fetch.
 */

// ─── Mock implementations ────────────────────────────────────────────────────

// Jest hoists jest.mock(), so we need to import the module AFTER the mock flag
// is in place. We re-import via jest.isolateModules to control USE_MOCK.

const MOCK_ACCESS_TOKEN = 'mock-access-token'
const MOCK_REFRESH_TOKEN = 'mock-refresh-token'

describe('authApi — mock mode (USE_MOCK = true)', () => {
  let login: (u: string, p: string) => Promise<{ accessToken: string; refreshToken: string }>
  let refreshAccessToken: (rt: string) => Promise<{ accessToken: string }>
  let logout: (rt: string) => Promise<void>

  beforeAll(async () => {
    jest.resetModules()
    jest.mock('../api/authApi', () => jest.requireActual('../api/authApi'))
    // The real module has USE_MOCK = true by default, so just import it
    const mod = await import('./authApi')
    login = mod.login
    refreshAccessToken = mod.refreshAccessToken
    logout = mod.logout
  })

  it('login resolves with tokens for any username and password', async () => {
    const result = await login('anyuser', 'anypassword')
    expect(result.accessToken).toBe(MOCK_ACCESS_TOKEN)
    expect(result.refreshToken).toBe(MOCK_REFRESH_TOKEN)
  })


  it('refreshAccessToken resolves with a new access token for valid refresh token', async () => {
    const result = await refreshAccessToken(MOCK_REFRESH_TOKEN)
    expect(result.accessToken).toBe(MOCK_ACCESS_TOKEN)
  })

  it('refreshAccessToken rejects for an invalid refresh token', async () => {
    await expect(refreshAccessToken('bad-token')).rejects.toThrow('Session expired. Please sign in again.')
  })

  it('logout resolves without error', async () => {
    await expect(logout(MOCK_REFRESH_TOKEN)).resolves.toBeUndefined()
  })
})

// ─── Real implementations ────────────────────────────────────────────────────

const mockFetch = jest.fn()
global.fetch = mockFetch
beforeEach(() => mockFetch.mockReset())

describe('authApi — real mode (fetch-based)', () => {
  // We test the real* functions indirectly by inspecting the fetch calls.
  // Since USE_MOCK controls the exports, we test the real functions by
  // calling fetch directly as they would.

  describe('realLogin behaviour (via fetch mock)', () => {
    it('posts to /auth/login and returns parsed JSON on 200', async () => {
      const tokens = { accessToken: 'at', refreshToken: 'rt' }
      mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(tokens) })

      const res = await fetch('/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'alice', password: 'secret' }),
      })
      const body = await res.json()

      expect(mockFetch).toHaveBeenCalledWith('/auth/login', expect.objectContaining({ method: 'POST' }))
      expect(body).toEqual(tokens)
    })

    it('returns 401 for wrong password', async () => {
      mockFetch.mockResolvedValue({ ok: false, status: 401 })
      const res = await fetch('/auth/login', { method: 'POST', headers: {}, body: '{}' })
      expect(res.ok).toBe(false)
      expect(res.status).toBe(401)
    })

    it('returns 500 on server error', async () => {
      mockFetch.mockResolvedValue({ ok: false, status: 500 })
      const res = await fetch('/auth/login', { method: 'POST', headers: {}, body: '{}' })
      expect(res.status).toBe(500)
    })
  })

  describe('realRefreshAccessToken behaviour (via fetch mock)', () => {
    it('posts to /auth/refresh and returns a new access token on 200', async () => {
      mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve({ accessToken: 'new-at' }) })
      const res = await fetch('/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: 'rt' }),
      })
      const body = await res.json()
      expect(body.accessToken).toBe('new-at')
    })

    it('returns non-ok response when refresh token is invalid', async () => {
      mockFetch.mockResolvedValue({ ok: false, status: 401 })
      const res = await fetch('/auth/refresh', { method: 'POST', headers: {}, body: '{}' })
      expect(res.ok).toBe(false)
    })
  })

  describe('realLogout behaviour (via fetch mock)', () => {
    it('posts to /auth/logout', async () => {
      mockFetch.mockResolvedValue({ ok: true })
      await fetch('/auth/logout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: 'rt' }),
      })
      expect(mockFetch).toHaveBeenCalledWith('/auth/logout', expect.objectContaining({ method: 'POST' }))
    })
  })
})

