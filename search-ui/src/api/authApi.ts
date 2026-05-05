export interface AuthTokens {
  accessToken: string
  refreshToken: string
}

export interface AuthStatus {
  enabled: boolean
}

// ─── Auth endpoints ─────────────────────────────────────────────────────────

export async function login(username: string, password: string): Promise<AuthTokens> {
  const res = await fetch('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!res.ok) {
    if (res.status === 401) throw new Error('Invalid password.')
    if (res.status === 400) throw new Error('Username and password must not be blank.')
    throw new Error(`Login failed (HTTP ${res.status})`)
  }
  return res.json()
}

/**
 * Exchanges a refresh token for a new access token **and** a rotated refresh token.
 * The caller is responsible for persisting the new refresh token.
 */
export async function refreshAccessToken(refreshToken: string): Promise<AuthTokens> {
  const res = await fetch('/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  if (!res.ok) {
    if (res.status === 401) throw new Error('Session expired. Please sign in again.')
    throw new Error(`Token refresh failed (HTTP ${res.status})`)
  }
  return res.json()
}

export async function logout(refreshToken: string): Promise<void> {
  await fetch('/auth/logout', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
}

/**
 * Fetches the backend auth status. Throws if the backend is unreachable.
 * GET /auth/status → { enabled: boolean }
 */
export async function getAuthStatus(): Promise<AuthStatus> {
  const res = await fetch('/auth/status')
  if (!res.ok) throw new Error(`Auth status check failed (HTTP ${res.status})`)
  return res.json()
}
