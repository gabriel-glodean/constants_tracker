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

  it('appends multi-select filters when provided', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve([]) })

    await getUnits('demo', 3, {
      filters: {
        types: ['String', 'Long'],
        semanticTypes: ['LOG_MESSAGE'],
        usageTypes: ['METHOD_INVOCATION_PARAMETER', 'FIELD_READ'],
      },
    })

    const call = mockFetch.mock.calls[0][0] as string
    const url = new URL(call, 'http://localhost')
    expect(url.pathname).toBe('/units')
    expect(url.searchParams.get('project')).toBe('demo')
    expect(url.searchParams.get('version')).toBe('3')
    expect(url.searchParams.getAll('type')).toEqual(['String', 'Long'])
    expect(url.searchParams.getAll('semanticType')).toEqual(['LOG_MESSAGE'])
    expect(url.searchParams.getAll('usageType')).toEqual(['METHOD_INVOCATION_PARAMETER', 'FIELD_READ'])
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

