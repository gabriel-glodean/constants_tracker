import { getMetadata } from './metadataApi'

const mockFetch = jest.fn()
global.fetch = mockFetch

beforeEach(() => mockFetch.mockReset())

describe('getMetadata', () => {
  it('fetches the combined metadata payload', async () => {
    const payload = {
      types: [{ name: 'String', displayName: 'String' }],
      usageTypes: [{ name: 'METHOD_INVOCATION_PARAMETER', displayName: 'Method Invocation Parameter' }],
      semanticTypes: [{ name: 'LOG_MESSAGE', displayName: 'Log Message' }],
    }
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(payload) })

    await expect(getMetadata()).resolves.toEqual(payload)
    expect(mockFetch).toHaveBeenCalledWith('/metadata', expect.objectContaining({ method: 'GET' }))
  })

  it('throws on non-ok responses', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 500 })
    await expect(getMetadata()).rejects.toThrow('Metadata lookup failed (HTTP 500)')
  })
})

