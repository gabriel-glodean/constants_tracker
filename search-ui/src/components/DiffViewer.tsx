import { useState } from 'react'
import {
  getDiff,
  type ProjectDiffResponse,
  type UnitDiff,
  type ConstantDiffEntry,
} from '@/api/diffApi'
import {
  AlertCircle,
  ChevronDown,
  ChevronRight,
  FilePlus,
  FileMinus,
  FileDiff,
  GitCompareArrows,
  GitCompare,
} from 'lucide-react'
function kindBadge(kind: ConstantDiffEntry['changeKind']) {
  switch (kind) {
    case 'ADDED':
      return <span className="px-1.5 py-0.5 rounded text-xs font-medium bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400">added</span>
    case 'REMOVED':
      return <span className="px-1.5 py-0.5 rounded text-xs font-medium bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400">removed</span>
    case 'CHANGED':
      return <span className="px-1.5 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400">changed</span>
  }
}
function unitIcon(unit: UnitDiff) {
  if (unit.addedUnit) return <FilePlus className="h-4 w-4 text-green-500 shrink-0" />
  if (unit.removedUnit) return <FileMinus className="h-4 w-4 text-red-500 shrink-0" />
  return <FileDiff className="h-4 w-4 text-amber-500 shrink-0" />
}
function unitLabel(unit: UnitDiff) {
  if (unit.addedUnit) return 'added'
  if (unit.removedUnit) return 'removed'
  return `${unit.changedConstants.length} constant change${unit.changedConstants.length !== 1 ? 's' : ''}`
}
function ConstantRow({ entry, fromVersion, toVersion }: {
  entry: ConstantDiffEntry
  fromVersion: number
  toVersion: number
}) {
  const [open, setOpen] = useState(false)
  return (
    <div className="border border-border rounded-lg overflow-hidden">
      <button
        className="w-full flex items-center gap-2 px-3 py-2 text-left hover:bg-secondary/50 transition-colors"
        onClick={() => setOpen(o => !o)}
      >
        {open ? <ChevronDown className="h-3.5 w-3.5 text-muted-foreground shrink-0" /> : <ChevronRight className="h-3.5 w-3.5 text-muted-foreground shrink-0" />}
        <span className="font-mono text-xs truncate flex-1 text-left">{entry.value}</span>
        <span className="text-xs text-muted-foreground mr-2">{entry.valueType}</span>
        {kindBadge(entry.changeKind)}
      </button>
      {open && (
        <div className="px-3 pb-3 grid grid-cols-2 gap-3 border-t border-border bg-secondary/20">
          {(['from', 'to'] as const).map(side => {
            const usages = side === 'from' ? entry.fromUsages : entry.toUsages
            const versionLabel = side === 'from' ? `since v${fromVersion}` : `since v${toVersion}`
            return (
              <div key={side}>
                <p className="text-xs font-medium text-muted-foreground mt-2 mb-1">{versionLabel}</p>
                {usages.length === 0 ? (
                  <p className="text-xs italic text-muted-foreground">—</p>
                ) : (
                  <ul className="space-y-1">
                    {usages.map((u, i) => (
                      <li key={i} className="text-xs">
                        <span className="font-medium">{u.structuralType}</span>
                        {u.semanticDisplayName && <span className="text-muted-foreground ml-1">({u.semanticDisplayName})</span>}
                        {u.locationClassName && (
                          <div className="font-mono text-muted-foreground truncate">
                            {u.locationClassName}{u.locationMethodName ? `#${u.locationMethodName}` : ''}{u.locationLineNumber != null ? `:${u.locationLineNumber}` : ''}
                          </div>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
function UnitRow({ unit, fromVersion, toVersion }: {
  unit: UnitDiff
  fromVersion: number
  toVersion: number
}) {
  const [open, setOpen] = useState(false)
  const hasConstants = unit.changedConstants.length > 0

  function toggle() {
    if (hasConstants) setOpen(o => !o)
  }

  return (
    <div className="border border-border rounded-xl overflow-hidden">
      <button
        className="w-full flex items-center gap-2 px-4 py-3 text-left hover:bg-secondary/40 transition-colors"
        onClick={toggle}
        onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle() } }}
        disabled={!hasConstants}
        aria-expanded={hasConstants ? open : undefined}
      >
        {hasConstants ? (open ? <ChevronDown className="h-4 w-4 text-muted-foreground shrink-0" /> : <ChevronRight className="h-4 w-4 text-muted-foreground shrink-0" />) : <span className="h-4 w-4 shrink-0" />}
        {unitIcon(unit)}
        <span className="font-mono text-sm flex-1 truncate text-left">{unit.path}</span>
        <span className="text-xs text-muted-foreground">{unitLabel(unit)}</span>
      </button>
      {open && hasConstants && (
        <div className="px-4 pb-4 space-y-2 border-t border-border">
          {unit.changedConstants.map((entry, i) => <ConstantRow key={i} entry={entry} fromVersion={fromVersion} toVersion={toVersion} />)}
        </div>
      )}
    </div>
  )
}
function DiffSummary({ diff }: { diff: ProjectDiffResponse }) {
  const added = diff.units.filter(u => u.addedUnit).length
  const removed = diff.units.filter(u => u.removedUnit).length
  const changed = diff.units.filter(u => !u.addedUnit && !u.removedUnit).length
  return (
    <div className="flex gap-4 text-sm mb-4">
      <span className="text-green-600 font-medium">+{added} added</span>
      <span className="text-red-600 font-medium">−{removed} removed</span>
      <span className="text-amber-600 font-medium">~{changed} changed</span>
      <span className="text-muted-foreground ml-auto">{diff.units.length} unit(s) total</span>
    </div>
  )
}
export function DiffViewer() {
  const [project, setProject] = useState('')
  const [fromV, setFromV] = useState('')
  const [toV, setToV] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [diff, setDiff] = useState<ProjectDiffResponse | null>(null)
  const [filter, setFilter] = useState<'all' | 'added' | 'removed' | 'changed'>('all')
  async function handleDiff(e: React.FormEvent) {
    e.preventDefault()
    if (!project.trim() || !fromV || !toV) return
    setLoading(true)
    setError(null)
    setDiff(null)
    try {
      const result = await getDiff(project.trim(), Number(fromV), Number(toV))
      setDiff(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Diff failed')
    } finally {
      setLoading(false)
    }
  }

  const counts = diff
    ? {
        all: diff.units.length,
        added: diff.units.filter(u => u.addedUnit).length,
        removed: diff.units.filter(u => u.removedUnit).length,
        changed: diff.units.filter(u => !u.addedUnit && !u.removedUnit).length,
      }
    : { all: 0, added: 0, removed: 0, changed: 0 }

  const filteredUnits = diff?.units.filter(u => {
    if (filter === 'added') return u.addedUnit
    if (filter === 'removed') return u.removedUnit
    if (filter === 'changed') return !u.addedUnit && !u.removedUnit
    return true
  }) ?? []

  return (
    <div className="space-y-6 max-w-3xl mx-auto">
      <form onSubmit={handleDiff} className="flex flex-wrap gap-2 items-end">
        <div className="flex-1 min-w-40">
          <label className="block text-xs text-muted-foreground mb-1">Project</label>
          <input type="text" placeholder="e.g. demo-crud-server" value={project} onChange={e => setProject(e.target.value)} className="w-full px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm" />
        </div>
        <div className="w-28">
          <label className="block text-xs text-muted-foreground mb-1">From version</label>
          <input type="number" placeholder="1" value={fromV} onChange={e => setFromV(e.target.value)} className="w-full px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm" />
        </div>
        <div className="w-28">
          <label className="block text-xs text-muted-foreground mb-1">To version</label>
          <input type="number" placeholder="2" value={toV} onChange={e => setToV(e.target.value)} className="w-full px-3 py-2 rounded-lg border border-input bg-secondary/50 text-sm" />
        </div>
        <button type="submit" disabled={loading || !project.trim() || !fromV || !toV} className="h-[38px] px-5 rounded-lg bg-primary text-primary-foreground text-sm font-medium flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed transition">
          <GitCompareArrows className="h-4 w-4" />
          {loading ? 'Loading…' : 'Compare'}
        </button>
      </form>

      {error && (
        <div className="flex items-center gap-3 p-4 rounded-xl border border-destructive/50 bg-destructive/5 text-sm">
          <AlertCircle className="h-5 w-5 text-destructive shrink-0" />
          <p className="text-destructive">{error}</p>
        </div>
      )}

      {loading && (
        <div className="flex items-center justify-center py-16">
          <div className="h-8 w-8 border-2 border-primary/30 border-t-primary rounded-full animate-spin" />
        </div>
      )}

      {/* ── Empty state — nothing loaded yet ── */}
      {!diff && !loading && !error && (
        <div className="flex flex-col items-center justify-center py-20 text-center select-none">
          <div className="h-16 w-16 rounded-2xl bg-secondary flex items-center justify-center mb-4">
            <GitCompare className="h-8 w-8 text-muted-foreground" />
          </div>
          <p className="text-base font-medium text-foreground mb-1">No diff loaded yet</p>
          <p className="text-sm text-muted-foreground max-w-xs">
            Enter a project name and two version numbers above, then click <span className="font-medium text-foreground">Compare</span>.
          </p>
        </div>
      )}

      {diff && !loading && (
        <div>
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-semibold text-base">{diff.project} — v{diff.fromVersion} → v{diff.toVersion}</h2>
          </div>
          {diff.units.length === 0 ? (
            <p className="text-sm text-muted-foreground py-8 text-center">No differences found between these versions.</p>
          ) : (
            <>
              <DiffSummary diff={diff} />
              <div className="flex gap-2 mb-4">
                {(['all', 'added', 'removed', 'changed'] as const).map(f => (
                  <button
                    key={f}
                    onClick={() => setFilter(f)}
                    className={`px-3 py-1 rounded-full text-xs font-medium transition-colors ${filter === f ? 'bg-primary text-primary-foreground' : 'bg-secondary text-muted-foreground hover:text-foreground'}`}
                  >
                    {f}
                    <span className={`ml-1.5 tabular-nums ${filter === f ? 'opacity-75' : 'opacity-60'}`}>
                      {counts[f]}
                    </span>
                  </button>
                ))}
              </div>
              <div className="space-y-2">
                {filteredUnits.map((unit, i) => <UnitRow key={i} unit={unit} fromVersion={diff.fromVersion} toVersion={diff.toVersion} />)}
                {filteredUnits.length === 0 && <p className="text-sm text-muted-foreground text-center py-6">No units match the selected filter.</p>}
              </div>
            </>
          )}
        </div>
      )}
    </div>
  )
}