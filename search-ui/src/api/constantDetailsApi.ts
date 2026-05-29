/**
 * Client for {@code GET /units/constants} — paged flat list of constant values with usage detail.
 *
 * Used by the filter panel to replace the brute-force per-unit constant fetching with a single
 * server-side filtered query.
 */
export interface ConstantDetailEntry {
  constantValue: string
  constantValueType: string
  structuralType: string
  semanticTypeKind: string
  semanticTypeName: string | null
  semanticDisplayName: string | null
  locationClassName: string
  locationMethodName: string
  locationMethodDescriptor: string
  locationLineNumber: number | null
  confidence: number
}
export interface ConstantDetailsFilter {
  /** Maps to {@code constant_usages.structural_type}, e.g. {@code METHOD_INVOCATION_PARAMETER}. */
  structuralType?: string
  /** Maps to the semantic type name as returned by {@code /metadata/semantic-types}. */
  semanticType?: string
  /** Maps to {@code unit_constants.constant_value_type}, e.g. {@code String}, {@code Integer}. */
  constantValueType?: string
}
export interface ConstantDetailsOptions extends ConstantDetailsFilter {
  fetcher?: typeof fetch
  /** Number of rows per page. Defaults to 200. */
  pageSize?: number
  /**
   * Maximum number of pages to fetch before throwing. Defaults to 500.
   * Guards against accidentally fetching millions of unfiltered rows.
   */
  maxPages?: number
}
/**
 * Fetches all pages of {@code GET /units/constants} and returns the combined result.
 *
 * Iteration stops as soon as a page returns fewer rows than {@code pageSize}, which signals
 * the last page.  All optional filters are sent as query params; absent filters are omitted.
 * Throws if {@code maxPages} (default 500) is exceeded to prevent runaway unfiltered fetches.
 */
export async function getAllConstantDetails(
  project: string,
  version: number,
  options?: ConstantDetailsOptions,
): Promise<ConstantDetailEntry[]> {
  const fetcher = options?.fetcher ?? globalThis.fetch
  const pageSize = options?.pageSize ?? 200
  const maxPages = options?.maxPages ?? 500
  const results: ConstantDetailEntry[] = []
  let page = 0
  for (;;) {
    const query = new URLSearchParams({
      project,
      version: String(version),
      pageSize: String(pageSize),
      page: String(page),
    })
    if (options?.structuralType)    query.set('structuralType',    options.structuralType)
    if (options?.semanticType)      query.set('semanticType',      options.semanticType)
    if (options?.constantValueType) query.set('constantValueType', options.constantValueType)
    const res = await fetcher(`/units/constants?${query.toString()}`, { method: 'GET' })
    if (!res.ok) throw new Error(`Constant details lookup failed (HTTP ${res.status})`)
    const rows: ConstantDetailEntry[] = await res.json()
    results.push(...rows)
    if (rows.length < pageSize) break   // last page
    page++
    if (page >= maxPages) {
      throw new Error(
        `Too many pages (>${maxPages}) fetching /units/constants — add a structural, semantic, or value-type filter to narrow the result.`,
      )
    }
  }
  return results
}