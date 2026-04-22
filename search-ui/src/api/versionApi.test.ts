import { getVersion, finalizeVersion, syncRemovals, deleteUnit } from './versionApi'
const mockFetch = jest.fn()
global.fetch = mockFetch
beforeEach(() => mockFetch.mockReset())
const ver = { id: 1, project: 'p', version: 1, parentVersion: null, status: 'OPEN', createdAt: '', finalizedAt: null }
describe('getVersion', () => {
  it('returns parsed JSON', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(ver) })
    const r = await getVersion('p', 1)
    expect(r).toEqual(ver)
    expect(mockFetch).toHaveBeenCalledWith('/project/p/version/1')
  })
  it('throws on 404', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 404 })
    await expect(getVersion('p', 1)).rejects.toThrow('Version not found.')
  })
  it('throws generic on other errors', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 500 })
    await expect(getVersion('p', 1)).rejects.toThrow('HTTP 500')
  })
})
describe('finalizeVersion', () => {
  it('POSTs and returns parsed JSON', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(ver) })
    const r = await finalizeVersion('p', 1)
    expect(r).toEqual(ver)
    expect(mockFetch).toHaveBeenCalledWith('/project/p/version/1/finalize', { method: 'POST' })
  })
  it('throws on 404', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 404 })
    await expect(finalizeVersion('p', 1)).rejects.toThrow('Version not found.')
  })
  it('throws on 409', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 409 })
    await expect(finalizeVersion('p', 1)).rejects.toThrow('already finalized')
  })
  it('throws generic on other errors', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 500 })
    await expect(finalizeVersion('p', 1)).rejects.toThrow('HTTP 500')
  })
})
describe('syncRemovals', () => {
  it('POSTs and returns list', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(['com/Foo']) })
    const r = await syncRemovals('p', 1)
    expect(r).toEqual(['com/Foo'])
  })
  it('throws on 409', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 409 })
    await expect(syncRemovals('p', 1)).rejects.toThrow('finalized')
  })
  it('throws generic on other errors', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 500 })
    await expect(syncRemovals('p', 1)).rejects.toThrow('HTTP 500')
  })
})
describe('deleteUnit', () => {
  it('DELETEs successfully', async () => {
    mockFetch.mockResolvedValue({ ok: true })
    await expect(deleteUnit('p', 1, 'com/Foo')).resolves.toBeUndefined()
    expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining('/class?'), { method: 'DELETE' })
  })
  it('throws on 409', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 409 })
    await expect(deleteUnit('p', 1, 'com/Foo')).rejects.toThrow('finalized')
  })
  it('throws generic on other errors', async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 500 })
    await expect(deleteUnit('p', 1, 'com/Foo')).rejects.toThrow('HTTP 500')
  })
})
