import { createAuthFetch } from './authFetch'

const mockGlobalFetch = jest.fn()
global.fetch = mockGlobalFetch
beforeEach(() => mockGlobalFetch.mockReset())

const TOKEN = 'access-token-abc'
const NEW_TOKEN = 'access-token-refreshed'

function makeOptions(overrides?: {
  getToken?: () => string | null
  refresh?: () => Promise<string>
  onNewToken?: (t: string) => void
}) {
  return {
    getToken: jest.fn(() => TOKEN as string | null),
    refresh: jest.fn(async () => NEW_TOKEN),
    onNewToken: jest.fn(),
    ...overrides,
  }
}

describe('createAuthFetch', () => {
  describe('happy path — no 401', () => {
    it('adds Authorization Bearer header to the request', async () => {
      mockGlobalFetch.mockResolvedValue({ status: 200, ok: true })
      const opts = makeOptions()
      const authFetch = createAuthFetch(opts)

      await authFetch('/api/data', { method: 'POST' })

      const [, init] = mockGlobalFetch.mock.calls[0]
      expect(new Headers(init.headers).get('Authorization')).toBe(`Bearer ${TOKEN}`)
    })

    it('passes the original request URL and options through', async () => {
      mockGlobalFetch.mockResolvedValue({ status: 200, ok: true })
      const opts = makeOptions()
      const authFetch = createAuthFetch(opts)

      await authFetch('/api/resource', { method: 'DELETE' })

      expect(mockGlobalFetch).toHaveBeenCalledWith('/api/resource', expect.objectContaining({ method: 'DELETE' }))
    })

    it('does not set Authorization header when getToken returns null', async () => {
      mockGlobalFetch.mockResolvedValue({ status: 200, ok: true })
      const opts = makeOptions({ getToken: () => null })
      const authFetch = createAuthFetch(opts)

      await authFetch('/api/data')

      const [, init] = mockGlobalFetch.mock.calls[0]
      expect(new Headers(init.headers).has('Authorization')).toBe(false)
    })

    it('returns the response from the first fetch call', async () => {
      const fakeResponse = { status: 200, ok: true, json: async () => ({ ok: true }) }
      mockGlobalFetch.mockResolvedValue(fakeResponse)
      const authFetch = createAuthFetch(makeOptions())

      const res = await authFetch('/api/data')

      expect(res).toBe(fakeResponse)
      expect(mockGlobalFetch).toHaveBeenCalledTimes(1)
    })

    it('does not call refresh when response is not 401', async () => {
      mockGlobalFetch.mockResolvedValue({ status: 403, ok: false })
      const opts = makeOptions()
      const authFetch = createAuthFetch(opts)

      await authFetch('/api/data')

      expect(opts.refresh).not.toHaveBeenCalled()
      expect(mockGlobalFetch).toHaveBeenCalledTimes(1)
    })
  })

  describe('token expiry — 401 with successful refresh', () => {
    it('retries the request with a new Bearer token after 401', async () => {
      mockGlobalFetch
        .mockResolvedValueOnce({ status: 401, ok: false })
        .mockResolvedValueOnce({ status: 200, ok: true })

      const opts = makeOptions()
      const authFetch = createAuthFetch(opts)

      await authFetch('/api/data', { method: 'GET' })

      expect(opts.refresh).toHaveBeenCalledTimes(1)
      expect(mockGlobalFetch).toHaveBeenCalledTimes(2)

      const [, retryInit] = mockGlobalFetch.mock.calls[1]
      expect(new Headers(retryInit.headers).get('Authorization')).toBe(`Bearer ${NEW_TOKEN}`)
    })

    it('calls onNewToken with the refreshed token', async () => {
      mockGlobalFetch
        .mockResolvedValueOnce({ status: 401, ok: false })
        .mockResolvedValueOnce({ status: 200, ok: true })

      const opts = makeOptions()
      const authFetch = createAuthFetch(opts)

      await authFetch('/api/data')

      expect(opts.onNewToken).toHaveBeenCalledWith(NEW_TOKEN)
    })

    it('returns the retry response', async () => {
      const retryResponse = { status: 200, ok: true }
      mockGlobalFetch
        .mockResolvedValueOnce({ status: 401, ok: false })
        .mockResolvedValueOnce(retryResponse)

      const authFetch = createAuthFetch(makeOptions())
      const res = await authFetch('/api/data')

      expect(res).toBe(retryResponse)
    })
  })

  describe('token expiry — 401 with failed refresh', () => {
    it('throws "Session expired." when refresh call fails', async () => {
      mockGlobalFetch.mockResolvedValue({ status: 401, ok: false })
      const opts = makeOptions({ refresh: async () => { throw new Error('No refresh token') } })
      const authFetch = createAuthFetch(opts)

      await expect(authFetch('/api/data')).rejects.toThrow('Session expired. Please sign in again.')
    })

    it('does not call onNewToken when refresh fails', async () => {
      mockGlobalFetch.mockResolvedValue({ status: 401, ok: false })
      const opts = makeOptions({ refresh: async () => { throw new Error('expired') } })
      const authFetch = createAuthFetch(opts)

      await expect(authFetch('/api/data')).rejects.toThrow()
      expect(opts.onNewToken).not.toHaveBeenCalled()
    })

    it('does not make a second fetch call when refresh fails', async () => {
      mockGlobalFetch.mockResolvedValue({ status: 401, ok: false })
      const opts = makeOptions({ refresh: async () => { throw new Error('expired') } })
      const authFetch = createAuthFetch(opts)

      await expect(authFetch('/api/data')).rejects.toThrow()
      expect(mockGlobalFetch).toHaveBeenCalledTimes(1)
    })
  })
})

