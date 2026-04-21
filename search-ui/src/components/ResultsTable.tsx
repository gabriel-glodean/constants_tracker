import { useState, useMemo } from 'react'
import { ChevronDown, ChevronRight, Copy, Check, FileCode2, Hash, FolderOpen } from 'lucide-react'
import type { FuzzySearchHit, FuzzySearchResponse } from '@/api/searchApi'

interface SemanticBadge {
  type: string
  confidence: string
}

function parseSemanticPairs(pairs: string[]): Map<string, SemanticBadge[]> {
  const map = new Map<string, SemanticBadge[]>()
  for (const pair of pairs) {
    const lastPipe = pair.lastIndexOf('|')
    if (lastPipe < 0) continue
    const secondLastPipe = pair.lastIndexOf('|', lastPipe - 1)
    if (secondLastPipe < 0) continue
    const value = pair.substring(0, secondLastPipe)
    const type = pair.substring(secondLastPipe + 1, lastPipe)
    const confidence = pair.substring(lastPipe + 1)
    const existing = map.get(value) ?? []
    // deduplicate by type
    if (!existing.some(b => b.type === type)) {
      existing.push({ type, confidence })
    }
    map.set(value, existing)
  }
  // Remove Unknown entries for constants that have at least one real classification
  for (const [value, badges] of map) {
    const hasReal = badges.some(b => b.type !== 'Unknown')
    if (hasReal) {
      map.set(value, badges.filter(b => b.type !== 'Unknown'))
    }
  }
  return map
}

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
          <HitRow key={`${hit.unitName}-${hit.version}-${idx}`} hit={hit} searchTerm={searchTerm} />
        ))}
      </div>
    </div>
  )
}

// ── Individual hit row ──────────────────────────────────────────────────

function HitRow({ hit, searchTerm }: { hit: FuzzySearchHit; searchTerm: string }) {
  const [expanded, setExpanded] = useState(false)
  const shortName = hit.unitName.split('/').pop() ?? hit.unitName
  const packagePath = hit.unitName.includes('/')
    ? hit.unitName.substring(0, hit.unitName.lastIndexOf('/'))
    : ''
  const semanticMap = useMemo(() => parseSemanticPairs(hit.semanticPairs ?? []), [hit.semanticPairs])

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
          {hit.sourceKind && (
            <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-400 text-xs font-medium">
              {hit.sourceKind}
            </span>
          )}
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
              <ConstantValueRow key={i} value={val} searchTerm={searchTerm} badges={semanticMap.get(val) ?? []} />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

// ── Constant value with highlight & copy ────────────────────────────────

const BADGE_COLORS: Record<string, string> = {
  'SQL Fragment': 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  'URL Resource': 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  'File Path': 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  'Log Message': 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
  'Error Message': 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  'Annotation': 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
}
const DEFAULT_BADGE_COLOR = 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'

function ConstantValueRow({ value, searchTerm, badges }: { value: string; searchTerm: string; badges: SemanticBadge[] }) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    await navigator.clipboard.writeText(value)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="flex items-start gap-3 px-3 py-2.5 group hover:bg-muted/50 transition-colors">
      <div className="flex-1 min-w-0">
        <code className="text-xs font-mono text-foreground/90 break-all whitespace-pre-wrap leading-relaxed">
          <HighlightedText text={value} highlight={searchTerm} />
        </code>
        {badges.length > 0 && (
          <div className="flex flex-wrap gap-1.5 mt-1.5">
            {badges.map((badge, i) => (
              <span
                key={i}
                className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-semibold ${BADGE_COLORS[badge.type] ?? DEFAULT_BADGE_COLOR}`}
              >
                {badge.type}
                <span className="opacity-70">{badge.confidence}</span>
              </span>
            ))}
          </div>
        )}
      </div>
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

