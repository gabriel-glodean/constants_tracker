import { getUnits } from './unitsApi'

const mockFetch = jest.fn()
global.fetch = mockFetch

beforeEach(() => mockFetch.mockReset())

describe('getUnits', () => {
  it('calls /units with project and version', async () => {
    const payload = [{ unitPath: 'app.jar', units: [{ name: 'com/Foo.class', constants: 2 }] }]
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(payload) })

    const result = await getUnits('demo', 3)

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/units?project=demo&version=3'),
      expect.objectContaining({ method: 'GET' })
    )
    expect(result).toEqual(payload)
  })

  it('returns an empty list on 404', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 404 })
    await expect(getUnits('demo', 9)).resolves.toEqual([])
  })

  it('throws for non-404 errors', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 500 })
    await expect(getUnits('demo', 9)).rejects.toThrow('Units lookup failed (HTTP 500)')
  })
})

