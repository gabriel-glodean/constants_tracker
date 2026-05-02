import { renderHook, act, waitFor } from '@testing-library/react'
import { useAuth } from './useAuth'
import * as authApi from '../api/authApi'

jest.mock('@/api/authApi')

const mockedLogin = authApi.login as jest.MockedFunction<typeof authApi.login>
const mockedRefresh = authApi.refreshAccessToken as jest.MockedFunction<typeof authApi.refreshAccessToken>
const mockedLogout = authApi.logout as jest.MockedFunction<typeof authApi.logout>

const REFRESH_TOKEN_KEY = 'ct_refresh_token'
const MOCK_TOKENS = { accessToken: 'at-123', refreshToken: 'rt-456' }

beforeEach(() => {
  jest.clearAllMocks()
  localStorage.clear()
})

describe('useAuth', () => {
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
      mockedRefresh.mockResolvedValue({ accessToken: 'at-restored' })

      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isLoading).toBe(false))

      expect(mockedRefresh).toHaveBeenCalledWith('rt-stored')
      expect(result.current.isAuthenticated).toBe(true)
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
      mockedRefresh.mockResolvedValue({ accessToken: MOCK_TOKENS.accessToken })
      mockedLogout.mockResolvedValue(undefined)

      const { result } = renderHook(() => useAuth())
      await waitFor(() => expect(result.current.isAuthenticated).toBe(true))

      await act(async () => { await result.current.signOut() })

      expect(mockedLogout).toHaveBeenCalledWith(MOCK_TOKENS.refreshToken)
      expect(result.current.isAuthenticated).toBe(false)
      expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull()
    })

    it('still clears state even when logout API call fails', async () => {
      localStorage.setItem(REFRESH_TOKEN_KEY, MOCK_TOKENS.refreshToken)
      mockedRefresh.mockResolvedValue({ accessToken: MOCK_TOKENS.accessToken })
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
})

