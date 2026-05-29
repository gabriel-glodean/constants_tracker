export interface MetadataOption {
  name: string
  displayName: string
}

export interface MetadataResponse {
  types: MetadataOption[]
  structuralTypes: MetadataOption[]
  semanticTypes: MetadataOption[]
}

export async function getMetadata(options?: { fetcher?: typeof fetch }): Promise<MetadataResponse> {
  const fetcher = options?.fetcher ?? globalThis.fetch
  const res = await fetcher('/metadata', { method: 'GET' })
  if (!res.ok) {
    throw new Error(`Metadata lookup failed (HTTP ${res.status})`)
  }
  return res.json()
}

