import { useCallback, useRef, useState } from 'react'
import { fuzzySearch, type FuzzySearchResponse, type SearchParams } from '@/api/searchApi'

interface SearchState {
  data: FuzzySearchResponse | null
  isLoading: boolean
  error: string | null
  hasSearched: boolean
}

export function useSearch() {
  const [state, setState] = useState<SearchState>({
    data: null,
    isLoading: false,
    error: null,
    hasSearched: false,
  })

  const abortRef = useRef<AbortController | null>(null)

  const search = useCallback(async (params: SearchParams) => {
    // Cancel any in-flight request
    abortRef.current?.abort()
    abortRef.current = new AbortController()

    setState(prev => ({ ...prev, isLoading: true, error: null }))

    try {
      const data = await fuzzySearch(params)
      setState({ data, isLoading: false, error: null, hasSearched: true })
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      setState(prev => ({
        ...prev,
        isLoading: false,
        error: err instanceof Error ? err.message : 'An unexpected error occurred',
        hasSearched: true,
      }))
    }
  }, [])

  const reset = useCallback(() => {
    setState({ data: null, isLoading: false, error: null, hasSearched: false })
  }, [])

  return { ...state, search, reset }
}

