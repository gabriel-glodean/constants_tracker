import { getClassConstants } from './classApi'
const mockFetch = jest.fn()
global.fetch = mockFetch
beforeEach(() => mockFetch.mockReset())
describe('getClassConstants', () => {
  const params = { project: 'proj', className: 'com/Foo', version: 1 }
  const sampleReply = { constants: { 'SELECT *': ['METHOD_INVOCATION_PARAMETER'] } }
  it('calls the correct URL and returns parsed JSON', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(sampleReply) })
    const result = await getClassConstants(params)
    expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining('/class?'), expect.objectContaining({ method: 'GET' }))
    expect(result).toEqual(sampleReply)
  })
  it('throws specific message on 404', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 404 })
    await expect(getClassConstants(params)).rejects.toThrow('Class/version not found.')
  })
  it('throws generic message on other errors', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 503 })
    await expect(getClassConstants(params)).rejects.toThrow('Lookup failed (HTTP 503)')
  })
})
