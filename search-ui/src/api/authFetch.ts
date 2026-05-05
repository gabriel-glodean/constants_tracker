export type AuthFetch = typeof fetch

export interface AuthFetchOptions {
  /** Returns the current in-memory access token. */
  getToken: () => string | null
  /** Fetches a new access token using the stored refresh token. */
  refresh: () => Promise<string>
  /** Called with the newly obtained access token so the hook can update state. */
  onNewToken: (token: string) => void
  /** Called when the refresh itself fails so the caller can clear session state. */
  onSessionExpired?: () => void
}

/**
 * Returns a drop-in `fetch` replacement that:
 *  1. Injects `Authorization: Bearer <token>` on every request.
 *  2. On a 401 response, silently refreshes the access token and retries once.
 *  3. If the refresh itself fails, calls onSessionExpired (if provided) then
 *     throws "Session expired. Please sign in again."
 */
export function createAuthFetch({ getToken, refresh, onNewToken, onSessionExpired }: AuthFetchOptions): AuthFetch {
  return async (input: RequestInfo | URL, init?: RequestInit) => {
    // ── First attempt ────────────────────────────────────────────────────────
    const token = getToken()
    const headers = new Headers(init?.headers)
    if (token) headers.set('Authorization', `Bearer ${token}`)

    const res = await globalThis.fetch(input, { ...init, headers })
    if (res.status !== 401) return res

    // ── 401: try to refresh then retry ───────────────────────────────────────
    let newToken: string
    try {
      newToken = await refresh()
      onNewToken(newToken)
    } catch {
      onSessionExpired?.()
      throw new Error('Session expired. Please sign in again.')
    }

    const retryHeaders = new Headers(init?.headers)
    retryHeaders.set('Authorization', `Bearer ${newToken}`)
    return globalThis.fetch(input, { ...init, headers: retryHeaders })
  }
}
