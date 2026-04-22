import { renderHook, act } from '@testing-library/react'
import { useSearch } from './useSearch'
import * as searchApi from '@/api/searchApi'
jest.mock('@/api/searchApi')
const mockedSearch = searchApi.fuzzySearch as jest.MockedFunction<typeof searchApi.fuzzySearch>
const params = { project: '*', term: 'hello', fuzzy: 1, rows: 25 }
const sampleData = { hits: [], totalFound: 0 }
beforeEach(() => jest.clearAllMocks())
describe('useSearch', () => {
  it('initial state is idle', () => {
    const { result } = renderHook(() => useSearch())
    expect(result.current.isLoading).toBe(false)
    expect(result.current.data).toBeNull()
    expect(result.current.error).toBeNull()
    expect(result.current.hasSearched).toBe(false)
  })
  it('sets isLoading during fetch and resolves data', async () => {
    mockedSearch.mockResolvedValue(sampleData)
    const { result } = renderHook(() => useSearch())
    await act(async () => { await result.current.search(params) })
    expect(result.current.isLoading).toBe(false)
    expect(result.current.data).toEqual(sampleData)
    expect(result.current.hasSearched).toBe(true)
  })
  it('sets error on failure', async () => {
    mockedSearch.mockRejectedValue(new Error('Search failed'))
    const { result } = renderHook(() => useSearch())
    await act(async () => { await result.current.search(params) })
    expect(result.current.error).toBe('Search failed')
    expect(result.current.hasSearched).toBe(true)
  })
  it('sets generic error for non-Error rejection', async () => {
    mockedSearch.mockRejectedValue('boom')
    const { result } = renderHook(() => useSearch())
    await act(async () => { await result.current.search(params) })
    expect(result.current.error).toBe('An unexpected error occurred')
  })
  it('reset clears state', async () => {
    mockedSearch.mockResolvedValue(sampleData)
    const { result } = renderHook(() => useSearch())
    await act(async () => { await result.current.search(params) })
    act(() => { result.current.reset() })
    expect(result.current.data).toBeNull()
    expect(result.current.hasSearched).toBe(false)
  })
  it('ignores AbortError', async () => {
    const abortErr = new DOMException('aborted', 'AbortError')
    mockedSearch.mockRejectedValue(abortErr)
    const { result } = renderHook(() => useSearch())
    await act(async () => { await result.current.search(params) })
    expect(result.current.error).toBeNull()
    expect(result.current.hasSearched).toBe(false)
  })
})
