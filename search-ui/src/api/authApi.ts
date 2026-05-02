export interface AuthTokens {
  accessToken: string
  refreshToken: string
}

// ─── Toggle this to false once the real backend is ready ───────────────────
const USE_MOCK = true
const MOCK_PASSWORD = 'admin'
const MOCK_ACCESS_TOKEN = 'mock-access-token'
const MOCK_REFRESH_TOKEN = 'mock-refresh-token'

function delay(ms = 600) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

// ─── Real implementations ───────────────────────────────────────────────────

async function realLogin(password: string): Promise<AuthTokens> {
  const res = await fetch('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password }),
  })
  if (!res.ok) {
    if (res.status === 401) throw new Error('Invalid password.')
    throw new Error(`Login failed (HTTP ${res.status})`)
  }
  return res.json()
}

async function realRefreshAccessToken(refreshToken: string): Promise<Pick<AuthTokens, 'accessToken'>> {
  const res = await fetch('/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  if (!res.ok) throw new Error('Session expired. Please sign in again.')
  return res.json()
}

async function realLogout(refreshToken: string): Promise<void> {
  await fetch('/auth/logout', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
}

// ─── Mock implementations ───────────────────────────────────────────────────

async function mockLogin(password: string): Promise<AuthTokens> {
  await delay()
  if (password !== MOCK_PASSWORD) throw new Error('Invalid password.')
  return { accessToken: MOCK_ACCESS_TOKEN, refreshToken: MOCK_REFRESH_TOKEN }
}

async function mockRefreshAccessToken(refreshToken: string): Promise<Pick<AuthTokens, 'accessToken'>> {
  await delay(300)
  if (refreshToken !== MOCK_REFRESH_TOKEN) throw new Error('Session expired. Please sign in again.')
  return { accessToken: MOCK_ACCESS_TOKEN }
}

async function mockLogout(): Promise<void> {
  await delay(200)
  // no-op in mock
}

// ─── Exported API (switches based on USE_MOCK) ───────────────────────────────

export const login              = USE_MOCK ? mockLogin              : realLogin
export const refreshAccessToken = USE_MOCK ? mockRefreshAccessToken : realRefreshAccessToken
export const logout             = USE_MOCK ? mockLogout             : realLogout
