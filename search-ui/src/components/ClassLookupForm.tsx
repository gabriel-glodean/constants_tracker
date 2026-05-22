import { useMemo, useState, type EventHandler, type SyntheticEvent } from 'react'
import { getClassConstants } from '@/api/classApi'
import { getUnits, type UnitGroup } from '@/api/unitsApi'
import { AlertCircle, FolderTree, FileCode2 } from 'lucide-react'

interface ClassLookupFormProps {
  project?: string
  version?: string
  fetcher?: typeof fetch
}

export function ClassLookupForm({ project: sharedProject, version: sharedVersion, fetcher }: ClassLookupFormProps) {
  const projectInputId = 'class-lookup-project'
  const versionInputId = 'class-lookup-version'
  const filterInputId = 'class-lookup-filter'

  const [project, setProject] = useState('')
  const [version, setVersion] = useState('')
  const [groups, setGroups] = useState<UnitGroup[]>([])
  const [selectedUnit, setSelectedUnit] = useState<string | null>(null)
  const [filter, setFilter] = useState('')
  const [result, setResult] = useState<{[k:string]: string[]} | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [loadingUnits, setLoadingUnits] = useState(false)

  const activeProject = (sharedProject ?? project).trim()
  const activeVersion = Number(sharedVersion ?? version)
  const hasScope = !!activeProject && Number.isFinite(activeVersion) && activeVersion > 0

  const filteredGroups = useMemo(() => {
    const normalized = filter.trim().toLowerCase()
    if (!normalized) return groups
    return groups
      .map(group => ({
        ...group,
        units: group.units.filter(u => u.name.toLowerCase().includes(normalized)),
      }))
      .filter(group => group.units.length > 0)
  }, [groups, filter])

  const handleSubmit: EventHandler<SyntheticEvent<HTMLFormElement>> = async e => {
    e.preventDefault()
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
      const data = await getUnits(activeProject, activeVersion, { fetcher })
      setGroups(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unit lookup failed')
    } finally {
      setLoadingUnits(false)
    }
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
      </form>

      {groups.length > 0 && (
        <div className="grid grid-cols-1 lg:grid-cols-[minmax(300px,1.2fr)_2fr] gap-4">
          <div className="border border-border rounded-xl bg-card/50 p-3 space-y-3">
            <div className="space-y-1">
              <label htmlFor={filterInputId} className="text-xs font-medium text-muted-foreground">Filter units</label>
              <input id={filterInputId} type="text" placeholder="Type class or file name" value={filter} onChange={e => setFilter(e.target.value)}
                className="w-full px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm" />
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
              <ul className="space-y-2">
                {Object.entries(result).map(([constant, usages]) => (
                  <li key={constant} className="flex flex-col gap-1">
                    <span className="font-mono text-xs bg-muted/40 rounded px-2 py-1 text-foreground break-all">{constant}</span>
                    <span className="text-xs text-muted-foreground">{usages.join(', ')}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}

      {error && <div className="flex items-center gap-2 text-destructive text-sm"><AlertCircle className="w-4 h-4"/>{error}</div>}
    </div>
  )
}

