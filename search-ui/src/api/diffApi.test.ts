import { getDiff } from './diffApi'

const mockFetch = jest.fn()
global.fetch = mockFetch

beforeEach(() => mockFetch.mockReset())

describe('getDiff', () => {
  const sampleResponse = {
    project: 'proj',
    fromVersion: 1,
    toVersion: 2,
    units: [],
  }

  it('calls the correct URL and returns parsed JSON', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(sampleResponse),
    })
    const result = await getDiff('proj', 1, 2)
    expect(mockFetch).toHaveBeenCalledWith('/project/proj/diff?from=1&to=2')
    expect(result).toEqual(sampleResponse)
  })

  it('encodes special characters in the project name', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(sampleResponse) })
    await getDiff('my project/v2', 1, 2)
    expect(mockFetch).toHaveBeenCalledWith('/project/my%20project%2Fv2/diff?from=1&to=2')
  })

  it('throws a specific message on HTTP 400', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 400 })
    await expect(getDiff('proj', 1, 2)).rejects.toThrow('Invalid diff parameters.')
  })

  it('throws a generic message on other HTTP errors', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 503 })
    await expect(getDiff('proj', 1, 2)).rejects.toThrow('Diff failed (HTTP 503)')
  })
})

