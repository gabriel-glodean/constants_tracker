import { type FormEvent, useState } from 'react'
import { Search, SlidersHorizontal } from 'lucide-react'
import type { SearchParams } from '@/api/searchApi'

interface SearchFormProps {
  onSearch: (params: SearchParams) => void
  isLoading: boolean
}

const FUZZY_OPTIONS = [
  { value: 0, label: 'Exact', description: 'No tolerance for typos' },
  { value: 1, label: '~1 edit', description: 'Tolerates one typo' },
  { value: 2, label: '~2 edits', description: 'Tolerates two typos' },
] as const

export function SearchForm({ onSearch, isLoading }: SearchFormProps) {
  const [term, setTerm] = useState('')
  const [project, setProject] = useState('*')
  const [fuzzy, setFuzzy] = useState(1)
  const [rows, setRows] = useState(25)
  const [showAdvanced, setShowAdvanced] = useState(false)

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!term.trim()) return
    onSearch({ project: project.trim() || '*', term: term.trim(), fuzzy, rows })
  }

  return (
    <form onSubmit={handleSubmit} className="w-full space-y-4">
      {/* Main search row */}
      <div className="flex gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4.5 w-4.5 text-muted-foreground pointer-events-none" />
          <input
            type="text"
            value={term}
            onChange={e => setTerm(e.target.value)}
            placeholder="Search constants… (e.g. SELECT, log4j, http://)"
            className="w-full h-12 pl-11 pr-4 rounded-xl border border-input bg-secondary/50 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:border-transparent transition-all"
          />
        </div>
        <button
          type="submit"
          disabled={isLoading || !term.trim()}
          className="h-12 px-6 rounded-xl bg-primary text-primary-foreground font-medium text-sm hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 focus:ring-offset-background disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center gap-2 cursor-pointer"
        >
          {isLoading ? (
            <div className="h-4 w-4 border-2 border-primary-foreground/30 border-t-primary-foreground rounded-full animate-spin" />
          ) : (
            <Search className="h-4 w-4" />
          )}
          Search
        </button>
      </div>

      {/* Advanced toggle */}
      <div className="flex items-center justify-between">
        <button
          type="button"
          onClick={() => setShowAdvanced(!showAdvanced)}
          className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
        >
          <SlidersHorizontal className="h-3.5 w-3.5" />
          {showAdvanced ? 'Hide' : 'Show'} advanced options
        </button>
        {project !== '*' && (
          <span className="text-xs text-muted-foreground">
            Filtering by project: <span className="text-foreground font-medium">{project}</span>
          </span>
        )}
      </div>

      {/* Advanced panel */}
      {showAdvanced && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 p-4 rounded-xl border border-border bg-card/50 animate-in fade-in-0 slide-in-from-top-1 duration-200">
          {/* Project */}
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
              Project
            </label>
            <input
              type="text"
              value={project}
              onChange={e => setProject(e.target.value)}
              placeholder="* (all projects)"
              className="w-full h-9 px-3 rounded-lg border border-input bg-secondary/50 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring transition-all"
            />
          </div>

          {/* Fuzzy distance */}
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
              Fuzzy tolerance
            </label>
            <div className="flex gap-1.5">
              {FUZZY_OPTIONS.map(opt => (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => setFuzzy(opt.value)}
                  title={opt.description}
                  className={`flex-1 h-9 rounded-lg text-xs font-medium transition-all cursor-pointer ${
                    fuzzy === opt.value
                      ? 'bg-primary text-primary-foreground shadow-sm'
                      : 'bg-secondary/50 text-muted-foreground hover:text-foreground border border-input'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* Rows */}
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
              Max results
            </label>
            <select
              value={rows}
              onChange={e => setRows(Number(e.target.value))}
              className="w-full h-9 px-3 rounded-lg border border-input bg-secondary/50 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-ring transition-all cursor-pointer"
            >
              {[10, 25, 50, 100].map(n => (
                <option key={n} value={n}>
                  {n} results
                </option>
              ))}
            </select>
          </div>
        </div>
      )}
    </form>
  )
}

