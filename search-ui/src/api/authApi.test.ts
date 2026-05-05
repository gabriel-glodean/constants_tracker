/**
 * Tests for authApi — all implementations are fetch-based (no mock mode).
 */

const mockFetch = jest.fn()
global.fetch = mockFetch
beforeEach(() => mockFetch.mockReset())

import { login, refreshAccessToken, logout, getAuthStatus } from './authApi'

describe('authApi — login', () => {
  it('posts to /auth/login and returns parsed tokens on 200', async () => {
    const tokens = { accessToken: 'at', refreshToken: 'rt' }
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(tokens) })

    const result = await login('alice', 'secret')

    expect(mockFetch).toHaveBeenCalledWith('/auth/login', expect.objectContaining({ method: 'POST' }))
    expect(result).toEqual(tokens)
  })

  it('throws "Invalid password." on 401', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 401 })
    await expect(login('alice', 'wrong')).rejects.toThrow('Invalid password.')
  })

  it('throws generic error on non-401 failure', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 500 })
    await expect(login('alice', 'pw')).rejects.toThrow('Login failed (HTTP 500)')
  })

  it('throws when blank credentials are rejected (HTTP 400)', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 400 })
    await expect(login('', '')).rejects.toThrow('Username and password must not be blank.')
  })
})

describe('authApi — refreshAccessToken', () => {
  it('posts to /auth/refresh and returns new access token and rotated refresh token on 200', async () => {
    const tokens = { accessToken: 'new-at', refreshToken: 'new-rt' }
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(tokens) })
    const result = await refreshAccessToken('rt')
    expect(mockFetch).toHaveBeenCalledWith('/auth/refresh', expect.objectContaining({ method: 'POST' }))
    expect(result.accessToken).toBe('new-at')
    expect(result.refreshToken).toBe('new-rt')
  })

  it('throws "Session expired." when refresh token is invalid', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 401 })
    await expect(refreshAccessToken('bad-rt')).rejects.toThrow('Session expired. Please sign in again.')
  })
})

describe('authApi — logout', () => {
  it('posts to /auth/logout', async () => {
    mockFetch.mockResolvedValue({ ok: true })
    await logout('rt')
    expect(mockFetch).toHaveBeenCalledWith('/auth/logout', expect.objectContaining({ method: 'POST' }))
  })
})

describe('authApi — getAuthStatus', () => {
  it('returns { enabled: true } when auth is enabled', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve({ enabled: true }) })
    const result = await getAuthStatus()
    expect(mockFetch).toHaveBeenCalledWith('/auth/status')
    expect(result).toEqual({ enabled: true })
  })

  it('returns { enabled: false } when auth is disabled', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve({ enabled: false }) })
    const result = await getAuthStatus()
    expect(result).toEqual({ enabled: false })
  })

  it('throws when the backend returns a non-ok response', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 503 })
    await expect(getAuthStatus()).rejects.toThrow('Auth status check failed (HTTP 503)')
  })

  it('throws when the backend is unreachable (network error)', async () => {
    mockFetch.mockRejectedValue(new TypeError('Failed to fetch'))
    await expect(getAuthStatus()).rejects.toThrow('Failed to fetch')
  })
})
