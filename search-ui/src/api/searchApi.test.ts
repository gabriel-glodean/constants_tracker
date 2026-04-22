import { fuzzySearch } from './searchApi'
const mockFetch = jest.fn()
global.fetch = mockFetch
beforeEach(() => mockFetch.mockReset())
describe('fuzzySearch', () => {
  const params = { project: '*', term: 'SELECT', fuzzy: 1, rows: 25 }
  const sampleReply = { hits: [], totalFound: 0 }
  it('calls the correct URL and returns parsed JSON', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(sampleReply) })
    const result = await fuzzySearch(params)
    expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining('/search?'))
    expect(result).toEqual(sampleReply)
  })
  it('throws specific message on 400', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 400 })
    await expect(fuzzySearch(params)).rejects.toThrow('Invalid search parameters.')
  })
  it('throws generic message on other errors', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 500 })
    await expect(fuzzySearch(params)).rejects.toThrow('Search failed (HTTP 500)')
  })
})
