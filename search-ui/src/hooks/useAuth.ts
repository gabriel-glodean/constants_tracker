import { useState, useEffect, useCallback } from 'react'
import { login as apiLogin, logout as apiLogout, refreshAccessToken } from '@/api/authApi'

const REFRESH_TOKEN_KEY = 'ct_refresh_token'

export function useAuth() {
  // Access token lives only in memory — never written to localStorage.
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const isAuthenticated = accessToken !== null

  // On mount: try to silently restore session from stored refresh token.
  useEffect(() => {
    const stored = localStorage.getItem(REFRESH_TOKEN_KEY)
    const restorePromise = stored
      ? refreshAccessToken(stored)
          .then(({ accessToken: newToken }: { accessToken: string }) => setAccessToken(newToken))
          .catch(() => localStorage.removeItem(REFRESH_TOKEN_KEY))
      : Promise.resolve()

    restorePromise.finally(() => setIsLoading(false))
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
  // Recreates when accessToken changes, so it always carries the latest token.
  const authFetch = useCallback<typeof fetch>(async (input, init) => {
    const headers = new Headers(init?.headers)
    if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)

    const res = await globalThis.fetch(input, { ...init, headers })
    if (res.status !== 401) return res

    // Token expired — attempt silent refresh
    const rt = localStorage.getItem(REFRESH_TOKEN_KEY)
    if (!rt) throw new Error('Session expired. Please sign in again.')

    try {
      const { accessToken: newAt } = await refreshAccessToken(rt)
      setAccessToken(newAt)
      const retryHeaders = new Headers(init?.headers)
      retryHeaders.set('Authorization', `Bearer ${newAt}`)
      return globalThis.fetch(input, { ...init, headers: retryHeaders })
    } catch {
      throw new Error('Session expired. Please sign in again.')
    }
  }, [accessToken])

  return { isAuthenticated, isLoading, accessToken, signIn, signOut, authFetch }
}
