import { getAllConstantDetails, ConstantDetailEntry } from './constantDetailsApi'

const mockFetch = jest.fn()

beforeEach(() => mockFetch.mockReset())

const makeEntry = (value: string): ConstantDetailEntry => ({
  constantValue: value,
  constantValueType: 'String',
  structuralType: 'METHOD_INVOCATION_PARAMETER',
  semanticTypeKind: 'CORE',
  semanticTypeName: 'LOG_MESSAGE',
  semanticDisplayName: 'Log Message',
  locationClassName: 'com/acme/Foo',
  locationMethodName: 'bar',
  locationMethodDescriptor: '()V',
  locationLineNumber: 10,
  confidence: 0.9,
})

describe('getAllConstantDetails', () => {
  it('fetches a single page when results are fewer than pageSize', async () => {
    const entries = [makeEntry('hello'), makeEntry('world')]
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(entries) })

    const result = await getAllConstantDetails('demo', 1, { fetcher: mockFetch, pageSize: 200 })

    expect(mockFetch).toHaveBeenCalledTimes(1)
    expect(result).toHaveLength(2)
    expect(result[0].constantValue).toBe('hello')
  })

  it('appends structuralType, semanticType and constantValueType query params when provided', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve([]) })

    await getAllConstantDetails('proj', 3, {
      fetcher: mockFetch,
      structuralType: 'FIELD_STORE',
      semanticType: 'LOG_MESSAGE',
      constantValueType: 'Integer',
      pageSize: 50,
    })

    const url = new URL(mockFetch.mock.calls[0][0] as string, 'http://localhost')
    expect(url.pathname).toBe('/units/constants')
    expect(url.searchParams.get('project')).toBe('proj')
    expect(url.searchParams.get('version')).toBe('3')
    expect(url.searchParams.get('pageSize')).toBe('50')
    expect(url.searchParams.get('page')).toBe('0')
    expect(url.searchParams.get('structuralType')).toBe('FIELD_STORE')
    expect(url.searchParams.get('semanticType')).toBe('LOG_MESSAGE')
    expect(url.searchParams.get('constantValueType')).toBe('Integer')
  })

  it('omits optional filter params when not provided', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve([]) })

    await getAllConstantDetails('proj', 1, { fetcher: mockFetch })

    const url = new URL(mockFetch.mock.calls[0][0] as string, 'http://localhost')
    expect(url.searchParams.has('structuralType')).toBe(false)
    expect(url.searchParams.has('semanticType')).toBe(false)
    expect(url.searchParams.has('constantValueType')).toBe(false)
  })

  it('paginates until a page returns fewer rows than pageSize', async () => {
    const fullPage = Array.from({ length: 3 }, (_, i) => makeEntry(`v${i}`))
    const lastPage = [makeEntry('last')]

    mockFetch
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(fullPage) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(fullPage) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(lastPage) })

    const result = await getAllConstantDetails('demo', 1, { fetcher: mockFetch, pageSize: 3 })

    expect(mockFetch).toHaveBeenCalledTimes(3)
    expect(result).toHaveLength(7) // 3 + 3 + 1
    // page numbers should increment
    const pages = mockFetch.mock.calls.map(
      ([url]: [string]) => new URL(url, 'http://localhost').searchParams.get('page'),
    )
    expect(pages).toEqual(['0', '1', '2'])
  })

  it('throws when a page returns a non-OK response', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 503 })

    await expect(
      getAllConstantDetails('demo', 1, { fetcher: mockFetch }),
    ).rejects.toThrow('Constant details lookup failed (HTTP 503)')
  })

  it('throws when maxPages is exceeded', async () => {
    const fullPage = [makeEntry('x'), makeEntry('y')] // length === pageSize=2

    // Always return a full page → infinite loop without cap
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(fullPage) })

    await expect(
      getAllConstantDetails('demo', 1, { fetcher: mockFetch, pageSize: 2, maxPages: 3 }),
    ).rejects.toThrow(/Too many pages/)

    // Should have made exactly maxPages calls before throwing
    expect(mockFetch).toHaveBeenCalledTimes(3)
  })

  it('uses globalThis.fetch when no fetcher option is provided', async () => {
    const globalFetch = jest.fn().mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve([]),
    })
    ;(globalThis as unknown as Record<string, unknown>).fetch = globalFetch

    await getAllConstantDetails('demo', 1)

    expect(globalFetch).toHaveBeenCalledTimes(1)
  })
})

