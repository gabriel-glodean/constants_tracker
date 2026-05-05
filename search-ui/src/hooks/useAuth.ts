import { useState, useEffect, useCallback, useMemo } from 'react'
import { login as apiLogin, logout as apiLogout, refreshAccessToken, getAuthStatus } from '@/api/authApi'
import { createAuthFetch } from '@/api/authFetch'

const REFRESH_TOKEN_KEY = 'ct_refresh_token'

export function useAuth() {
  // Access token lives only in memory — never written to localStorage.
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [authRequired, setAuthRequired] = useState(true)
  const [backendAvailable, setBackendAvailable] = useState(true)

  const isAuthenticated = accessToken !== null
  /** True when the user can access protected features (auth disabled OR signed in). */
  const canAccess = !authRequired || isAuthenticated

  // On mount: probe /auth/status + silently restore session from stored refresh token.
  useEffect(() => {
    const statusPromise = getAuthStatus()
      .then(({ enabled }) => {
        setAuthRequired(enabled)
        setBackendAvailable(true)
      })
      .catch(() => {
        setBackendAvailable(false)
        // Keep authRequired as true so login UI still shows when connectivity returns.
      })

    const stored = localStorage.getItem(REFRESH_TOKEN_KEY)
    const restorePromise = stored
      ? refreshAccessToken(stored)
          .then(({ accessToken: newToken, refreshToken: newRt }: { accessToken: string; refreshToken: string }) => {
            localStorage.setItem(REFRESH_TOKEN_KEY, newRt)
            setAccessToken(newToken)
          })
          .catch(() => localStorage.removeItem(REFRESH_TOKEN_KEY))
      : Promise.resolve()

    Promise.all([statusPromise, restorePromise]).finally(() => setIsLoading(false))
  }, [])

  const signIn = useCallback(async (username: string, password: string) => {
    const { accessToken: at, refreshToken: rt } = await apiLogin(username, password)
    localStorage.setItem(REFRESH_TOKEN_KEY, rt)
    setAccessToken(at)
  }, [])

  const signOut = useCallback(async () => {
    const rt = localStorage.getItem(REFRESH_TOKEN_KEY)
    if (rt) {
      await apiLogout(rt).catch(() => {/* best-effort */})
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    }
    setAccessToken(null)
  }, [])

  // Injects Bearer token on every request and retries once on 401.
  // Recreated when accessToken changes so it always carries the latest token.
  // Uses createAuthFetch — no logic is duplicated here.
  const authFetch = useMemo(() => createAuthFetch({
    getToken: () => accessToken,
    refresh: async () => {
      const rt = localStorage.getItem(REFRESH_TOKEN_KEY)
      if (!rt) throw new Error('No refresh token')
      const { accessToken: newAt, refreshToken: newRt } = await refreshAccessToken(rt)
      localStorage.setItem(REFRESH_TOKEN_KEY, newRt)
      return newAt
    },
    onNewToken: setAccessToken,
    onSessionExpired: () => {
      setAccessToken(null)
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    },
  }), [accessToken])

  return { isAuthenticated, isLoading, authRequired, backendAvailable, canAccess, accessToken, signIn, signOut, authFetch }
}