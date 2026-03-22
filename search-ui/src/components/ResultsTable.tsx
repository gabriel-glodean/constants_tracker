import { useState } from 'react'
import { ChevronDown, ChevronRight, Copy, Check, FileCode2, Hash, FolderOpen } from 'lucide-react'
import type { FuzzySearchHit, FuzzySearchResponse } from '@/api/searchApi'

interface ResultsTableProps {
  data: FuzzySearchResponse
  searchTerm: string
}

export function ResultsTable({ data, searchTerm }: ResultsTableProps) {
  if (data.hits.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 px-4">
        <div className="h-16 w-16 rounded-2xl bg-secondary flex items-center justify-center mb-4">
          <FileCode2 className="h-8 w-8 text-muted-foreground" />
        </div>
        <h3 className="text-lg font-semibold text-foreground mb-1">No results found</h3>
        <p className="text-sm text-muted-foreground text-center max-w-md">
          No constants matching "<span className="text-foreground font-medium">{searchTerm}</span>" were found.
          Try adjusting your search term or increasing the fuzzy tolerance.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {/* Results header */}
      <div className="flex items-center justify-between px-1">
        <p className="text-sm text-muted-foreground">
          Showing <span className="text-foreground font-semibold">{data.hits.length}</span> of{' '}
          <span className="text-foreground font-semibold">{data.totalFound}</span> results
        </p>
      </div>

      {/* Results list */}
      <div className="border border-border rounded-xl overflow-hidden divide-y divide-border">
        {data.hits.map((hit, idx) => (
          <HitRow key={`${hit.className}-${hit.version}-${idx}`} hit={hit} searchTerm={searchTerm} />
        ))}
      </div>
    </div>
  )
}

// ── Individual hit row ──────────────────────────────────────────────────

function HitRow({ hit, searchTerm }: { hit: FuzzySearchHit; searchTerm: string }) {
  const [expanded, setExpanded] = useState(false)
  const shortName = hit.className.split('/').pop() ?? hit.className
  const packagePath = hit.className.includes('/')
    ? hit.className.substring(0, hit.className.lastIndexOf('/'))
    : ''

  return (
    <div className="bg-card/30 hover:bg-card/60 transition-colors">
      {/* Row header */}
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-3 px-4 py-3.5 text-left cursor-pointer"
      >
        <div className="text-muted-foreground">
          {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-semibold text-foreground">{shortName}</span>
            {packagePath && (
              <span className="text-xs text-muted-foreground font-mono truncate">{packagePath.replaceAll('/', '.')}</span>
            )}
          </div>
        </div>

        <div className="flex items-center gap-3 shrink-0">
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-primary/10 text-primary text-xs font-medium">
            <FolderOpen className="h-3 w-3" />
            {hit.project}
          </span>
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-secondary text-muted-foreground text-xs font-medium">
            <Hash className="h-3 w-3" />
            v{hit.version}
          </span>
          <span className="text-xs text-muted-foreground tabular-nums">
            {hit.constantValues.length} constant{hit.constantValues.length !== 1 ? 's' : ''}
          </span>
        </div>
      </button>

      {/* Expanded detail */}
      {expanded && (
        <div className="px-4 pb-4 pl-11">
          <div className="rounded-lg border border-border bg-muted/30 divide-y divide-border overflow-hidden">
            {hit.constantValues.map((val, i) => (
              <ConstantValueRow key={i} value={val} searchTerm={searchTerm} />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

// ── Constant value with highlight & copy ────────────────────────────────

function ConstantValueRow({ value, searchTerm }: { value: string; searchTerm: string }) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    await navigator.clipboard.writeText(value)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="flex items-start gap-3 px-3 py-2.5 group hover:bg-muted/50 transition-colors">
      <code className="flex-1 text-xs font-mono text-foreground/90 break-all whitespace-pre-wrap leading-relaxed">
        <HighlightedText text={value} highlight={searchTerm} />
      </code>
      <button
        onClick={handleCopy}
        title="Copy to clipboard"
        className="shrink-0 p-1 rounded text-muted-foreground opacity-0 group-hover:opacity-100 hover:text-foreground hover:bg-accent transition-all cursor-pointer"
      >
        {copied ? <Check className="h-3.5 w-3.5 text-green-500" /> : <Copy className="h-3.5 w-3.5" />}
      </button>
    </div>
  )
}

// ── Text highlight helper ───────────────────────────────────────────────

function HighlightedText({ text, highlight }: { text: string; highlight: string }) {
  if (!highlight.trim()) return <>{text}</>

  const escaped = highlight.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const parts = text.split(new RegExp(`(${escaped})`, 'gi'))

  return (
    <>
      {parts.map((part, i) =>
        part.toLowerCase() === highlight.toLowerCase() ? (
          <mark key={i} className="bg-primary/20 text-primary-foreground rounded-sm px-0.5">
            {part}
          </mark>
        ) : (
          <span key={i}>{part}</span>
        ),
      )}
    </>
  )
}

