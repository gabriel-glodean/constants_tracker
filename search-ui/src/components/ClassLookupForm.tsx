import { useEffect, useMemo, useState, type EventHandler, type SyntheticEvent } from 'react'
import { getClassConstants, type ConstantEntry } from '@/api/classApi'
import { getMetadata, type MetadataOption, type MetadataResponse } from '@/api/metadataApi'
import { getAllConstantDetails } from '@/api/constantDetailsApi'
import { getUnits, type UnitGroup } from '@/api/unitsApi'
import { AlertCircle, ChevronDown, FolderTree, FileCode2, SlidersHorizontal } from 'lucide-react'

interface ClassLookupFormProps {
  project?: string
  version?: string
  fetcher?: typeof fetch
}

interface AppliedFilters {
  types: string[]
  semanticTypes: string[]
  structuralTypes: string[]
}

interface MetadataMultiSelectGroupProps {
  label: string
  options: MetadataOption[]
  selected: string[]
  onToggle: (value: string) => void
}

function MetadataMultiSelectGroup({ label, options, selected, onToggle }: MetadataMultiSelectGroupProps) {
  return (
    <div className="space-y-2">
      <div className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">{label}</div>
      <div className="max-h-48 overflow-auto space-y-1 rounded-lg border border-border bg-background/40 p-2">
        {options.length > 0 ? options.map(option => {
          const isChecked = selected.includes(option.name)
          return (
            <label
              key={option.name}
              className="flex cursor-pointer items-start gap-2 rounded-md px-2 py-1.5 text-xs hover:bg-secondary/60"
            >
              <input
                type="checkbox"
                checked={isChecked}
                onChange={() => onToggle(option.name)}
                className="mt-0.5 h-4 w-4 rounded border-input text-primary focus:ring-ring"
              />
              <span className="min-w-0 flex-1">
                <span className="block truncate font-medium text-foreground">
                  {option.displayName || option.name}
                </span>
                {option.displayName && option.displayName !== option.name && (
                  <span className="block truncate font-mono text-[10px] text-muted-foreground">
                    {option.name}
                  </span>
                )}
              </span>
            </label>
          )
        }) : (
          <div className="px-2 py-1.5 text-xs text-muted-foreground">No {label.toLowerCase()} metadata available.</div>
        )}
      </div>
    </div>
  )
}

export function ClassLookupForm({ project: sharedProject, version: sharedVersion, fetcher }: ClassLookupFormProps) {
  const projectInputId = 'class-lookup-project'
  const versionInputId = 'class-lookup-version'
  const filterInputId = 'class-lookup-filter'

  const [project, setProject] = useState('')
  const [version, setVersion] = useState('')
  const [groups, setGroups] = useState<UnitGroup[]>([])
  const [hasLoadedUnits, setHasLoadedUnits] = useState(false)
  const [matchingUnits, setMatchingUnits] = useState<Set<string> | null>(null)
  const [applyingFilters, setApplyingFilters] = useState(false)
  const [filtersNotice, setFiltersNotice] = useState('')
  const [selectedUnit, setSelectedUnit] = useState<string | null>(null)
  const [filter, setFilter] = useState('')
  const [result, setResult] = useState<ConstantEntry[] | null>(null)
  const [metadata, setMetadata] = useState<MetadataResponse | null>(null)
  const [loadingMetadata, setLoadingMetadata] = useState(false)
  const [metadataError, setMetadataError] = useState('')
  const [showAdvancedFilters, setShowAdvancedFilters] = useState(false)
  const [selectedTypes, setSelectedTypes] = useState<string[]>([])
  const [selectedSemanticTypes, setSelectedSemanticTypes] = useState<string[]>([])
  const [selectedStructuralTypes, setselectedStructuralTypes] = useState<string[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [loadingUnits, setLoadingUnits] = useState(false)

  useEffect(() => {
    let alive = true

    async function loadMetadata() {
      setLoadingMetadata(true)
      setMetadataError('')
      try {
        const data = await getMetadata({ fetcher })
        if (alive) setMetadata(data)
      } catch (err) {
        if (alive) setMetadataError(err instanceof Error ? err.message : 'Metadata lookup failed')
      } finally {
        if (alive) setLoadingMetadata(false)
      }
    }

    void loadMetadata()

    return () => {
      alive = false
    }
  }, [fetcher])

  const activeProject = (sharedProject ?? project).trim()
  const activeVersion = Number(sharedVersion ?? version)
  const hasScope = !!activeProject && Number.isFinite(activeVersion) && activeVersion > 0

  const filterSummary = [
    selectedTypes.length,
    selectedSemanticTypes.length,
    selectedStructuralTypes.length,
  ].reduce((sum, count) => sum + count, 0)

  const metadataStatus = loadingMetadata
    ? 'Loading metadata filters'
    : metadata
      ? 'Metadata filters loaded'
      : metadataError
        ? 'Metadata filters unavailable'
        : 'Metadata filters idle'

  const filteredGroups = useMemo(() => {
    const normalized = filter.trim().toLowerCase()
    const metadataFiltered = matchingUnits == null
      ? groups
      : groups
        .map(group => ({
          ...group,
          units: group.units.filter(unit => matchingUnits.has(unit.name)),
        }))
        .filter(group => group.units.length > 0)

    if (!normalized) return metadataFiltered
    return metadataFiltered
      .map(group => ({
        ...group,
        units: group.units.filter(u => u.name.toLowerCase().includes(normalized)),
      }))
      .filter(group => group.units.length > 0)
  }, [groups, filter, matchingUnits])

  async function applyMetadataFilters(filtersToApply: AppliedFilters, sourceGroups: UnitGroup[] = groups) {
    setFiltersNotice('')

    const hasAnyFilter =
      filtersToApply.types.length > 0 ||
      filtersToApply.semanticTypes.length > 0 ||
      filtersToApply.structuralTypes.length > 0

    if (!hasAnyFilter) {
      setMatchingUnits(null)
      return
    }

    setApplyingFilters(true)
    try {
      // Build the cross-product of all three axes so that OR-within-category and
      // AND-between-categories semantics are preserved:
      //   (A OR B) AND (X OR Y) AND (P OR Q)  ≡  union of all (A,X,P), (A,X,Q), ...
      // Absent selections expand to a single undefined entry (= no server-side filter for that axis).
      // All three axes are pushed server-side — constantValueType is now a DB column filter.
      const structuralAxis: (string | undefined)[] = filtersToApply.structuralTypes.length > 0
        ? filtersToApply.structuralTypes
        : [undefined]
      const semanticAxis: (string | undefined)[] = filtersToApply.semanticTypes.length > 0
        ? filtersToApply.semanticTypes
        : [undefined]
      const valueTypeAxis: (string | undefined)[] = filtersToApply.types.length > 0
        ? filtersToApply.types
        : [undefined]

      // Build a set of all known unit names for reverse-mapping locationClassName → unit name.
      const allUnitNames = new Set(sourceGroups.flatMap(g => g.units.map(u => u.name)))

      const seenRowKey = new Set<string>()
      const matchedUnitNames = new Set<string>()

      for (const structuralType of structuralAxis) {
        for (const semanticType of semanticAxis) {
          for (const constantValueType of valueTypeAxis) {
            const rows = await getAllConstantDetails(activeProject, activeVersion, {
              structuralType,
              semanticType,
              constantValueType,
              fetcher,
            })

            for (const row of rows) {
              // Deduplicate across overlapping calls.
              const key = `${row.locationClassName}\x00${row.constantValue}\x00${row.structuralType}\x00${row.semanticTypeName ?? ''}`
              if (seenRowKey.has(key)) continue
              seenRowKey.add(key)

              // Map locationClassName (JVM internal format, e.g. com/acme/Foo) to unit name
              // (e.g. com/acme/Foo.class).  Config-file units may already match without the suffix.
              const withSuffix = row.locationClassName + '.class'
              if (allUnitNames.has(withSuffix)) {
                matchedUnitNames.add(withSuffix)
              } else if (allUnitNames.has(row.locationClassName)) {
                matchedUnitNames.add(row.locationClassName)
              }
            }
          }
        }
      }

      setMatchingUnits(matchedUnitNames)
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      setFiltersNotice(`Filter failed: ${message}`)
      setMatchingUnits(null)
    } finally {
      setApplyingFilters(false)
    }
  }

  function toggleValue(selectedValues: string[], value: string, setSelectedValues: (values: string[]) => void) {
    setSelectedValues(
      selectedValues.includes(value)
        ? selectedValues.filter(item => item !== value)
        : [...selectedValues, value],
    )
  }

  async function loadUnits() {
    setError('')
    setLoadingUnits(true)
    setResult(null)
    setSelectedUnit(null)

    if (!hasScope) {
      setLoadingUnits(false)
      setError('Project and version are required to load units.')
      return
    }

    try {
      const data = await getUnits(activeProject, activeVersion, {
        fetcher,
        filters: {
          types: selectedTypes,
          semanticTypes: selectedSemanticTypes,
          structuralTypes: selectedStructuralTypes,
        },
      })

      setGroups(data)
      // Reset the filter view — the user can explicitly re-apply filters via the button.
      setMatchingUnits(null)
      setHasLoadedUnits(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unit lookup failed')
    } finally {
      setHasLoadedUnits(true)
      setLoadingUnits(false)
    }
  }

  // Stable color classes per value type — Tailwind classes must be written in full so the
  // purger keeps them. Fallback for unknown types uses a neutral style.
  const VALUE_TYPE_CLASSES: Record<string, string> = {
    String:          'bg-blue-500/15 text-blue-700 border-blue-400/30 dark:text-blue-300',
    Character:       'bg-sky-500/15 text-sky-700 border-sky-400/30 dark:text-sky-300',
    Integer:         'bg-emerald-500/15 text-emerald-700 border-emerald-400/30 dark:text-emerald-300',
    Long:            'bg-emerald-500/15 text-emerald-700 border-emerald-400/30 dark:text-emerald-300',
    Short:           'bg-emerald-500/15 text-emerald-700 border-emerald-400/30 dark:text-emerald-300',
    Byte:            'bg-emerald-500/15 text-emerald-700 border-emerald-400/30 dark:text-emerald-300',
    Float:           'bg-cyan-500/15 text-cyan-700 border-cyan-400/30 dark:text-cyan-300',
    Double:          'bg-cyan-500/15 text-cyan-700 border-cyan-400/30 dark:text-cyan-300',
    Boolean:         'bg-violet-500/15 text-violet-700 border-violet-400/30 dark:text-violet-300',
    Null:            'bg-zinc-500/15 text-zinc-500 border-zinc-400/30 dark:text-zinc-400',
    MethodHandle:    'bg-orange-500/15 text-orange-700 border-orange-400/30 dark:text-orange-300',
    DynamicConstant: 'bg-rose-500/15 text-rose-700 border-rose-400/30 dark:text-rose-300',
    ClassDescriptor: 'bg-indigo-500/15 text-indigo-700 border-indigo-400/30 dark:text-indigo-300',
    ArrayDesc:       'bg-purple-500/15 text-purple-700 border-purple-400/30 dark:text-purple-300',
  }

  function valueTypeBadgeClasses(valueType: string): string {
    return VALUE_TYPE_CLASSES[valueType]
      ?? 'bg-muted/50 text-muted-foreground border-border'
  }

  const handleSubmit: EventHandler<SyntheticEvent<HTMLFormElement>> = async e => {    e.preventDefault()
    await loadUnits()
  }

  async function lookupUnit(unitName: string) {
    setLoading(true)
    setError('')
    setResult(null)
    setSelectedUnit(unitName)
    try {
      const data = await getClassConstants({
        project: activeProject,
        className: unitName,
        version: activeVersion,
      }, { fetcher })
      setResult(data.constants)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Lookup failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      <form onSubmit={handleSubmit} className="space-y-4">
        {sharedProject == null || sharedVersion == null ? (
          <div className="grid grid-cols-[1fr_7.5rem] gap-3 items-end">
            <div className="space-y-1">
              <label htmlFor={projectInputId} className="text-xs font-medium text-muted-foreground">
                Project <span className="text-destructive">*</span>
              </label>
              <input id={projectInputId} name="project" type="text" placeholder="e.g. demo-crud-server" value={project} onChange={e=>setProject(e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm"/>
            </div>
            <div className="space-y-1">
              <label htmlFor={versionInputId} className="text-xs font-medium text-muted-foreground">
                Version <span className="text-destructive">*</span>
              </label>
              <input id={versionInputId} name="version" type="number" placeholder="e.g. 1" value={version} onChange={e=>setVersion(e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm"/>
            </div>
          </div>
        ) : (
          <div className="text-xs text-muted-foreground bg-secondary/40 border border-border rounded-lg px-3 py-2">
            Browsing project <span className="font-medium text-foreground">{activeProject}</span> version <span className="font-medium text-foreground">{activeVersion}</span>
          </div>
        )}

        <button type="submit" disabled={loadingUnits || !hasScope} className="w-full h-11 rounded-xl bg-primary text-primary-foreground font-medium text-base flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed">
          {loadingUnits ? <span className="animate-spin h-5 w-5 border-2 border-primary-foreground/30 border-t-primary-foreground rounded-full"/> : <FolderTree className="h-5 w-5"/>}
          Load Units
        </button>

        <span className="sr-only" aria-live="polite">{metadataStatus}</span>

      </form>

      {hasLoadedUnits && (
        <div className="grid grid-cols-1 lg:grid-cols-[minmax(300px,1.2fr)_2fr] gap-4">
          <div className="border border-border rounded-xl bg-card/50 p-3 space-y-3">
            <div className="space-y-1">
              <label htmlFor={filterInputId} className="text-xs font-medium text-muted-foreground">Filter units</label>
              <input id={filterInputId} type="text" placeholder="Type class or file name" value={filter} onChange={e => setFilter(e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm" />
            </div>
            <div className="space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <button
                  type="button"
                  onClick={() => setShowAdvancedFilters(value => !value)}
                  className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
                >
                  <SlidersHorizontal className="h-3.5 w-3.5" />
                  {showAdvancedFilters ? 'Hide' : 'Show'} advanced filters
                  <ChevronDown className={`h-3.5 w-3.5 transition-transform ${showAdvancedFilters ? 'rotate-180' : ''}`} />
                </button>
                {filterSummary > 0 && (
                  <span className="text-xs text-muted-foreground">
                    {filterSummary} metadata filter{filterSummary === 1 ? '' : 's'} selected
                  </span>
                )}
              </div>
              {showAdvancedFilters && (
                <div className="space-y-3 rounded-lg border border-border bg-card/50 p-3 animate-in fade-in-0 slide-in-from-top-1 duration-200">
                  {loadingMetadata && (
                    <div className="flex items-center gap-2 text-xs text-muted-foreground">
                      <span className="h-4 w-4 animate-spin rounded-full border-2 border-muted-foreground/30 border-t-muted-foreground" />
                      Loading metadata filters…
                    </div>
                  )}
                  {metadataError && <div className="text-xs text-destructive">{metadataError}</div>}
                  {metadata && (
                    <div className="grid grid-cols-1 gap-3">
                      <MetadataMultiSelectGroup
                        label="Type"
                        options={metadata.types}
                        selected={selectedTypes}
                        onToggle={value => toggleValue(selectedTypes, value, setSelectedTypes)}
                      />
                      <MetadataMultiSelectGroup
                        label="Semantic type"
                        options={metadata.semanticTypes}
                        selected={selectedSemanticTypes}
                        onToggle={value => toggleValue(selectedSemanticTypes, value, setSelectedSemanticTypes)}
                      />
                      <MetadataMultiSelectGroup
                        label="Structural type"
                        options={metadata.structuralTypes}
                        selected={selectedStructuralTypes}
                        onToggle={value => toggleValue(selectedStructuralTypes, value, setselectedStructuralTypes)}
                      />
                    </div>
                  )}
                  {filtersNotice && <div className="text-xs text-muted-foreground">{filtersNotice}</div>}
                  <button
                    type="button"
                    onClick={() => {
                      void applyMetadataFilters({
                        types: selectedTypes,
                        semanticTypes: selectedSemanticTypes,
                        structuralTypes: selectedStructuralTypes,
                      })
                    }}
                    disabled={loadingUnits || applyingFilters || !hasScope || !hasLoadedUnits}
                    className="w-full rounded-lg border border-input bg-secondary/50 px-3 py-2 text-xs font-medium text-foreground transition hover:bg-secondary disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {applyingFilters ? 'Applying filters…' : 'Apply filters'}
                  </button>
                </div>
              )}
            </div>
            <div className="max-h-[28rem] overflow-auto space-y-3">
              {filteredGroups.map(group => (
                <div key={group.unitPath} className="space-y-1.5">
                  <div className="text-xs font-semibold text-foreground border-b border-border pb-1">{group.unitPath}</div>
                  <ul className="space-y-1">
                    {group.units.map(unit => (
                      <li key={`${group.unitPath}::${unit.name}`}>
                        <button
                          type="button"
                          onClick={() => lookupUnit(unit.name)}
                          className={`w-full text-left text-xs rounded-md px-2 py-1.5 transition flex items-center justify-between gap-2 ${selectedUnit === unit.name ? 'bg-primary/10 text-primary' : 'hover:bg-secondary/60'}`}
                        >
                          <span className="truncate flex items-center gap-1.5"><FileCode2 className="h-3.5 w-3.5 shrink-0" />{unit.name}</span>
                          <span className="text-[10px] text-muted-foreground tabular-nums">{unit.constants}</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
              {filteredGroups.length === 0 && (
                <p className="text-xs text-muted-foreground">No units match the current filter.</p>
              )}
            </div>
          </div>

          <div className="border border-border rounded-xl bg-card/50 p-4 min-h-[16rem]">
            <h3 className="font-semibold mb-2 text-base">Constants</h3>
            {!selectedUnit && !loading && !result && (
              <p className="text-sm text-muted-foreground">Select a class or file from the left panel to inspect constants.</p>
            )}
            {loading && (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <span className="animate-spin h-4 w-4 border-2 border-muted-foreground/30 border-t-muted-foreground rounded-full" />
                Loading constants for <span className="font-mono">{selectedUnit}</span>
              </div>
            )}
            {result && (
              <ul className="space-y-3">
                {result.map((entry, idx) => (
                  <li key={`${entry.value}-${idx}`} className="flex flex-col gap-1.5 border border-border rounded-lg p-2.5 bg-muted/20">
                    <div className="flex items-start gap-2 flex-wrap">
                      <span className="font-mono text-xs bg-muted/40 rounded px-2 py-1 text-foreground break-all flex-1">{entry.value}</span>
                      {entry.valueType && (
                        <span className={`shrink-0 text-[10px] font-medium rounded-full px-2 py-0.5 border ${valueTypeBadgeClasses(entry.valueType)}`}>
                          {entry.valueType}
                        </span>
                      )}
                    </div>
                    <div className="flex flex-wrap gap-1">
                      {entry.usages.map((usage, uidx) => (
                        <span key={uidx} className="flex items-center gap-1">
                          <span className="text-[10px] rounded bg-secondary px-1.5 py-0.5 text-muted-foreground font-mono">
                            {usage.structuralType}
                          </span>
                          {usage.semanticType && (
                            <span
                              className="text-[10px] rounded bg-accent px-1.5 py-0.5 text-accent-foreground"
                              title={usage.semanticType.description ?? undefined}
                            >
                              {usage.semanticType.displayName ?? usage.semanticType.name}
                            </span>
                          )}
                        </span>
                      ))}
                    </div>
                  </li>
                ))}
                {result.length === 0 && (
                  <p className="text-xs text-muted-foreground">No constants found.</p>
                )}
              </ul>
            )}
          </div>
        </div>
      )}

      {error && <div className="flex items-center gap-2 text-destructive text-sm"><AlertCircle className="w-4 h-4"/>{error}</div>}
    </div>
  )
}

