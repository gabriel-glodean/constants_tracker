import { renderHook, act, waitFor } from '@testing-library/react'
import { useAuth } from './useAuth'
import * as authApi from '../api/authApi'

jest.mock('@/api/authApi')

const mockedLogin = authApi.login as jest.MockedFunction<typeof authApi.login>
const mockedRefresh = authApi.refreshAccessToken as jest.MockedFunction<typeof authApi.refreshAccessToken>
const mockedLogout = authApi.logout as jest.MockedFunction<typeof authApi.logout>
const mockedGetAuthStatus = authApi.getAuthStatus as jest.MockedFunction<typeof authApi.getAuthStatus>

const REFRESH_TOKEN_KEY = 'ct_refresh_token'
const MOCK_TOKENS = { accessToken: 'at-123', refreshToken: 'rt-456' }
const MOCK_REFRESH_RESPONSE = { accessToken: 'at-restored', refreshToken: 'rt-rotated' }

const mockGlobalFetch = jest.fn()
global.fetch = mockGlobalFetch

beforeEach(() => {
  jest.clearAllMocks()
  localStorage.clear()
  mockGlobalFetch.mockReset()
  // Default: backend available, auth required
  mockedGetAuthStatus.mockResolvedValue({ enabled: true })
})

describe('useAuth', () => {
  describe('backend status', () => {
    it('sets backendAvailable=true and authRequired=true when /auth/status returns enabled:true', async () => {
      mockedGetAuthStatus.mockResolvedValue({ enabled: true })
      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isLoading).toBe(false))
      expect(result.current.backendAvailable).toBe(true)
      expect(result.current.authRequired).toBe(true)
    })

    it('sets authRequired=false and canAccess=true when /auth/status returns enabled:false', async () => {
      mockedGetAuthStatus.mockResolvedValue({ enabled: false })
      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isLoading).toBe(false))
      expect(result.current.authRequired).toBe(false)
      expect(result.current.canAccess).toBe(true)
    })

    it('sets backendAvailable=false when /auth/status fetch fails', async () => {
      mockedGetAuthStatus.mockRejectedValue(new TypeError('Failed to fetch'))
      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isLoading).toBe(false))
      expect(result.current.backendAvailable).toBe(false)
    })
  })

  describe('initial load — no stored refresh token', () => {
    it('starts unauthenticated when localStorage is empty', async () => {
      mockedRefresh.mockResolvedValue({ accessToken: 'at' })
      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isLoading).toBe(false))
      expect(result.current.isAuthenticated).toBe(false)
      expect(mockedRefresh).not.toHaveBeenCalled()
    })
  })

  describe('initial load — stored refresh token', () => {
    it('silently restores session when a valid refresh token is in localStorage', async () => {
      localStorage.setItem(REFRESH_TOKEN_KEY, 'rt-stored')
      mockedRefresh.mockResolvedValue(MOCK_REFRESH_RESPONSE)

      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isLoading).toBe(false))

      expect(mockedRefresh).toHaveBeenCalledWith('rt-stored')
      expect(result.current.isAuthenticated).toBe(true)
      // rotated refresh token must be persisted
      expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe(MOCK_REFRESH_RESPONSE.refreshToken)
    })

    it('clears localStorage and stays unauthenticated when refresh fails', async () => {
      localStorage.setItem(REFRESH_TOKEN_KEY, 'expired-rt')
      mockedRefresh.mockRejectedValue(new Error('Session expired.'))

      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isLoading).toBe(false))

      expect(result.current.isAuthenticated).toBe(false)
      expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull()
    })
  })

  describe('canAccess', () => {
    it('is false when auth is required and user is not signed in', async () => {
      mockedGetAuthStatus.mockResolvedValue({ enabled: true })
      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isLoading).toBe(false))
      expect(result.current.canAccess).toBe(false)
    })

    it('is true when auth is required and user is signed in', async () => {
      mockedGetAuthStatus.mockResolvedValue({ enabled: true })
      mockedLogin.mockResolvedValue(MOCK_TOKENS)
      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isLoading).toBe(false))
      await act(async () => { await result.current.signIn('alice', 'pw') })
      expect(result.current.canAccess).toBe(true)
    })

    it('is true when auth is not required regardless of authentication state', async () => {
      mockedGetAuthStatus.mockResolvedValue({ enabled: false })
      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isLoading).toBe(false))
      expect(result.current.isAuthenticated).toBe(false)
      expect(result.current.canAccess).toBe(true)
    })
  })

  describe('signIn', () => {
    it('sets isAuthenticated and stores refresh token in localStorage on success', async () => {
      mockedRefresh.mockRejectedValue(new Error('no stored token'))
      mockedLogin.mockResolvedValue(MOCK_TOKENS)

      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isLoading).toBe(false))

      await act(async () => { await result.current.signIn('alice', 'admin') })

      expect(mockedLogin).toHaveBeenCalledWith('alice', 'admin')
      expect(result.current.isAuthenticated).toBe(true)
      expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe(MOCK_TOKENS.refreshToken)
    })

    it('throws and stays unauthenticated on wrong password', async () => {
      mockedRefresh.mockRejectedValue(new Error())
      mockedLogin.mockRejectedValue(new Error('Invalid password.'))

      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isLoading).toBe(false))

      await expect(act(async () => { await result.current.signIn('alice', 'wrong') })).rejects.toThrow('Invalid password.')
      expect(result.current.isAuthenticated).toBe(false)
    })
  })

  describe('signOut', () => {
    it('clears isAuthenticated, removes localStorage entry, and calls logout API', async () => {
      localStorage.setItem(REFRESH_TOKEN_KEY, MOCK_TOKENS.refreshToken)
      mockedRefresh.mockResolvedValue({ accessToken: MOCK_TOKENS.accessToken, refreshToken: 'rt-rotated' })
      mockedLogout.mockResolvedValue(undefined)

      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true))

      await act(async () => { await result.current.signOut() })

      // After initial restore the refresh token in localStorage is the rotated one.
      expect(mockedLogout).toHaveBeenCalledWith('rt-rotated')
      expect(result.current.isAuthenticated).toBe(false)
      expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull()
    })

    it('still clears state even when logout API call fails', async () => {
      localStorage.setItem(REFRESH_TOKEN_KEY, MOCK_TOKENS.refreshToken)
      mockedRefresh.mockResolvedValue({ accessToken: MOCK_TOKENS.accessToken, refreshToken: 'rt-rotated' })
      mockedLogout.mockRejectedValue(new Error('Network error'))

      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true))

      await act(async () => { await result.current.signOut() })

      expect(result.current.isAuthenticated).toBe(false)
      expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull()
    })

    it('is a no-op when already signed out', async () => {
      mockedRefresh.mockRejectedValue(new Error())
      mockedLogout.mockResolvedValue(undefined)

      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isLoading).toBe(false))

      await act(async () => { await result.current.signOut() })

      expect(mockedLogout).not.toHaveBeenCalled()
      expect(result.current.isAuthenticated).toBe(false)
    })
  })

  describe('authFetch', () => {
    it('adds Authorization Bearer header when signed in', async () => {
      localStorage.setItem(REFRESH_TOKEN_KEY, MOCK_TOKENS.refreshToken)
      mockedRefresh.mockResolvedValue({ accessToken: MOCK_TOKENS.accessToken, refreshToken: 'rt-rotated' })
      mockGlobalFetch.mockResolvedValue({ status: 200, ok: true })

      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true))

      await act(async () => { await result.current.authFetch('/api/data') })

      const [, init] = mockGlobalFetch.mock.calls[0]
      expect(new Headers(init.headers).get('Authorization')).toBe(`Bearer ${MOCK_TOKENS.accessToken}`)
    })

    it('does not add Authorization header when not signed in', async () => {
      mockedRefresh.mockRejectedValue(new Error())
      mockGlobalFetch.mockResolvedValue({ status: 200, ok: true })

      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isLoading).toBe(false))

      await act(async () => { await result.current.authFetch('/api/data') })

      const [, init] = mockGlobalFetch.mock.calls[0]
      expect(new Headers(init.headers).has('Authorization')).toBe(false)
    })

    it('refreshes token and retries on 401 response', async () => {
      localStorage.setItem(REFRESH_TOKEN_KEY, MOCK_TOKENS.refreshToken)
      mockedRefresh
        .mockResolvedValueOnce({ accessToken: MOCK_TOKENS.accessToken, refreshToken: 'rt-rotated-1' })  // initial restore
        .mockResolvedValueOnce({ accessToken: 'at-refreshed', refreshToken: 'rt-rotated-2' })           // 401 retry

      mockGlobalFetch
        .mockResolvedValueOnce({ status: 401, ok: false })
        .mockResolvedValueOnce({ status: 200, ok: true })

      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true))

      await act(async () => { await result.current.authFetch('/api/data') })

      expect(mockGlobalFetch).toHaveBeenCalledTimes(2)
      const [, retryInit] = mockGlobalFetch.mock.calls[1]
      expect(new Headers(retryInit.headers).get('Authorization')).toBe('Bearer at-refreshed')
      // rotated refresh token from retry must be persisted
      expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('rt-rotated-2')
    })

    it('clears session state and throws when refresh fails on 401', async () => {
      localStorage.setItem(REFRESH_TOKEN_KEY, MOCK_TOKENS.refreshToken)
      mockedRefresh
        .mockResolvedValueOnce({ accessToken: MOCK_TOKENS.accessToken, refreshToken: 'rt-rotated' })  // initial restore
        .mockRejectedValueOnce(new Error('Session expired.'))                                          // 401 retry fails

      mockGlobalFetch.mockResolvedValue({ status: 401, ok: false })

      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true))

      // Catch the error inside act so that the setAccessToken(null) state update
      // (fired from onSessionExpired before the throw) is flushed within the act boundary.
      let caughtError: Error | null = null
      await act(async () => {
        try {
          await result.current.authFetch('/api/data')
        } catch (e) {
          caughtError = e as Error
        }
      })

      expect(caughtError?.message).toBe('Session expired. Please sign in again.')
      expect(result.current.isAuthenticated).toBe(false)
      expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull()
    })
  })
})