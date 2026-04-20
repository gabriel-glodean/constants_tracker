// ── Types matching the backend DTOs ─────────────────────────────────────

export interface FuzzySearchHit {
  project: string
  unitName: string
  version: number
  sourceKind: string
  constantValues: string[]
  semanticPairs: string[]
}

export interface FuzzySearchResponse {
  hits: FuzzySearchHit[]
  totalFound: number
}

export interface SearchParams {
  project: string
  term: string
  fuzzy: number
  rows: number
}

// ── API client ──────────────────────────────────────────────────────────

const API_BASE = '' // proxied via Vite dev server in development

export async function fuzzySearch(params: SearchParams): Promise<FuzzySearchResponse> {
  const qs = new URLSearchParams({
    project: params.project,
    term: params.term,
    fuzzy: String(params.fuzzy),
    rows: String(params.rows),
  })

  const res = await fetch(`${API_BASE}/search?${qs.toString()}`)

  if (!res.ok) {
    if (res.status === 400) {
      throw new Error('Invalid search parameters. Please check your input.')
    }
    throw new Error(`Search failed (HTTP ${res.status})`)
  }

  return res.json() as Promise<FuzzySearchResponse>
}

